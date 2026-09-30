package com.newvent.editor.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.newvent.common.exception.code.ErrorCode;
import com.newvent.editor.exception.EditErrorCode;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.LlmDailyLimitExceededException;
import com.newvent.generation.service.EventGuard;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.HtmlPolicy;
import com.newvent.generation.service.LlmCallRecorder;
import com.newvent.generation.service.RequestFilter;
import com.newvent.generation.service.RetryService;
import com.newvent.generation.service.VersionStore;
import com.newvent.infra.llm.LlmCallContext;
import com.newvent.infra.llm.LlmCallException;
import com.newvent.infra.llm.LlmCallGateway;
import com.newvent.registry.Block;
import com.newvent.registry.BlockMerge;
import com.newvent.registry.BlockValidator;
import com.newvent.registry.PromptBuilder;

/**
 * 관리자의 채팅 요청 하나를 새 버전까지
 * ★ 모델을 두 종류로 부른다
 *     라우터 1회      "무엇을 어디에" 를 정한다. 재시도 없다
 *     블록 수정 N회    영역마다 재시도 루프 하나. 연산 수만큼 묶음이 생긴다
 *   한 요청의 상한은 {@code 1 + N × maxAttempts()} 다. N 은 최대 3
 *
 * ★ 하나라도 막히면 아무것도 안 한다

 * ★ 저장은 마지막에 한 번뿐이다
 *   연산이 3개여도 버전은 1행이다. 중간에 실패하거나 중단되면 0행이다.

 */
@Service
public class EditService {

    private static final Logger log = LoggerFactory.getLogger(EditService.class);

    private static final Gate GATE = new Gate();

    private final RouteService router;
    private final RetryService retry;
    private final VersionStore versions;
    private final EventGuard guard;
    private final GenerationJobStore jobs;
    private final LlmCallRecorder recorder;


    private final LlmCallGateway gateway;


    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "edit");
        t.setDaemon(true);
        return t;
    });

    public EditService(RouteService router, RetryService retry, VersionStore versions,
                       EventGuard guard, GenerationJobStore jobs,
                       LlmCallRecorder recorder, LlmCallGateway gateway) {
        this.router = router;
        this.retry = retry;
        this.versions = versions;
        this.guard = guard;
        this.jobs = jobs;
        this.recorder = recorder;
        this.gateway = gateway;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    // ── 시작 ──────────────────────────────────────────────────────

    /** 시작 결과. 거부도 정상 흐름이라 예외로 던지지 않는다 */
    public sealed interface StartResult {
        record Started(GenerationJob job) implements StartResult {}

        /** 요청 자체가 잘못됐다. 컨트롤러가 EditException 으로 바꿔 던진다 */
        record Rejected(ErrorCode errorCode) implements StartResult {}

        /** 그 이벤트에 생성이나 수정이 이미 돌고 있다 — 409 */
        record AlreadyRunning(GenerationJob job) implements StartResult {}
    }


    public StartResult start(EditCommand cmd) {
        // ① 이벤트를 건드려도 되는가
        Optional<ErrorCode> blocked = guard.rejectReason(cmd.eventId());
        if (blocked.isPresent()) {
            return new StartResult.Rejected(blocked.get());
        }

        // ② 입력
        Optional<GenerationErrorCode> bad = RequestFilter.reject(cmd.requestText());
        if (bad.isPresent()) {
            return new StartResult.Rejected(bad.get());
        }

        // ③ 고칠 게 있는가 — 여기서 봐야 404 가 관리자에게 도달한다
        if (versions.latest(cmd.eventId()).isEmpty()) {
            return new StartResult.Rejected(EditErrorCode.PAGE_NOT_FOUND);
        }

        // ④ 이미 돌고 있나 — **자리를 잡지 않고** 먼저 본다
        Optional<GenerationJob> already = jobs.ofEvent(cmd.eventId());
        if (already.isPresent()) {
            return new StartResult.AlreadyRunning(already.get());
        }

        // ⑤ 하루 상한 — 라우터 1회 + 연산 하나의 재시도 묶음
        gateway.reserve(1 + retry.maxAttempts());

        // ⑥ 자리 잡기
        Optional<GenerationJob> slot = jobs.start(cmd.eventId());
        if (slot.isEmpty()) {
            // ★ 그 사이에 **끝났을** 수도 있다. orElseThrow 면 500 이다
            return jobs.ofEvent(cmd.eventId())
                    .<StartResult>map(StartResult.AlreadyRunning::new)
                    .orElseGet(() -> new StartResult.Rejected(EditErrorCode.ALREADY_RUNNING));
        }

        GenerationJob job = slot.get();
        worker.submit(() -> runSafely(job, cmd));
        return new StartResult.Started(job);
    }

    /** 중단 요청 — 즉시 멈추지 않는다. 연산 사이에서 멈춘다 */
    public boolean cancel(GenerationJob job) {
        if (job.done()) return false;
        job.requestCancel();
        return true;
    }

    // ── 실행 ──────────────────────────────────────────────────────

    /** ★ 여기서 모든 예외를 잡는다. 워커 스레드 밖으로 나가면 아무도 못 본다 */
    private void runSafely(GenerationJob job, EditCommand cmd) {
        try {
            run(job, cmd);
        } catch (RetryService.Aborted e) {
            // ★ 시도 기록은 apply() 가 이미 남겼다 — ctx 가 거기 있다.
            log.warn("수정 실패 — {}차 시도에서 모델 호출 (event={})", e.attempt(), cmd.eventId(), e);
            job.fail("수정 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (LlmDailyLimitExceededException e) {
            // ★ 429 를 HTTP 로 못 보낸다. 이미 202 가 나갔다.
            //   문구를 따로 두는 이유 — 이건 고장이 아니라 내일 되는 일이다
            log.warn("수정 중단 — 하루 호출 상한 (event={}, 상한={}, 사용={})",
                    cmd.eventId(), e.getDailyLimit(), e.getUsed());
            job.fail("오늘 사용할 수 있는 횟수를 모두 썼습니다. 내일 다시 시도해 주세요.");
        } catch (LlmCallException e) {
            log.warn("수정 실패 — 모델 호출 (event={})", cmd.eventId(), e);
            job.fail("수정 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (IllegalArgumentException e) {
            // ★ BlockMerge 다. 검증은 통과했는데 병합 자리를 못 찾았다 —
            log.error("수정 실패 — 블록 병합 (event={}): {}", cmd.eventId(), e.getMessage(), e);
            job.fail("수정 결과를 페이지에 반영하지 못했습니다. 다시 시도해 주세요.");
        } catch (Exception e) {
            log.error("수정 실패 — 예상치 못한 오류 (event={})", cmd.eventId(), e);
            job.fail("페이지를 고치는 중 문제가 생겼습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            // ★ 반드시 비운다. 안 비우면 그 이벤트는 다음 작업을 영원히 못 한다
            jobs.finish(job);
        }
    }

    private void run(GenerationJob job, EditCommand cmd) {
        job.to(GenerationJob.Phase.PREPARING);
        if (job.checkCancelled()) return;

        // ★ 기준 버전을 여기서 한 번 읽어 끝까지 들고 간다.
        //   저장할 때 다시 읽으면 그 사이에 생긴 버전을 기준으로 삼게 된다
        VersionStore.Snapshot base = versions.latest(cmd.eventId()).orElse(null);
        if (base == null) {
            // start() 에서 이미 봤다. 그 사이에 없어질 경로는 없지만 NPE 로 죽지는 않게 둔다
            job.fail("아직 만들어진 페이지가 없습니다. 먼저 페이지를 만들어 주세요.");
            return;
        }

        String doc = base.html();

        List<Decision.Run> plan = planAll(job, cmd, doc);
        if (plan == null) return;               // 되묻기 · 거절 — job 에 문구가 담겼다
        if (job.checkCancelled()) return;

        // ★ 모델을 부르는 연산만 센다. DELETE 는 모델을 안 부른다 —
        int modelOps = (int) plan.stream().filter(r -> r.op() != Op.DELETE).count();
        if (modelOps > 0) {
            // checkDailyLimit 은 상태가 없어서 다시 보는 게 맞다.
            gateway.reserve(modelOps * retry.maxAttempts());
        }
        for (Decision.Run step : plan) {
            if (job.checkCancelled()) return;
            doc = apply(job, cmd, doc, step);
            if (doc == null) return;            // 실패 — job 에 문구가 담겼다
        }
        if (job.checkCancelled()) return;

        // ★ 연산을 다 통과한 뒤 한 번만 옮긴다. 진행률은 뒤로 가지 않는다
        job.to(GenerationJob.Phase.VALIDATING);

        job.to(GenerationJob.Phase.SAVING);
        VersionStore.Saved saved = versions.save(cmd.eventId(), doc, base.versionId());
        log.info("수정 저장 (event={}, versionId={}, v{}, source=v{}) — 연산 {}개",
                cmd.eventId(), saved.versionId(), saved.versionNo(),
                base.versionNo(), plan.size());

        // ★★ 순서가 중요하다 — versionId 를 먼저 넣고 DONE 을 나중에 넣는다
        job.versionId(saved.versionId());
        job.to(GenerationJob.Phase.DONE);
    }

    // ── 라우터 + 관문 ─────────────────────────────────────────────

    /**
     * 요청문을 실행 계획으로 바꾼다. <b>모델을 부르는 건 라우터 1회뿐이다.</b>
     *
     */
    private List<Decision.Run> planAll(GenerationJob job, EditCommand cmd, String doc) {
        job.to(GenerationJob.Phase.ROUTING);

        // ★ requestId = jobId 다. 라우터 호출만 이 작업과 로그에서 이어진다.
        LlmCallContext ctx = LlmCallContext.of(cmd.eventId(), job.jobId());

        Optional<List<RawRoute>> routed = router.route(ctx, RequestFilter.clean(cmd.requestText()));
        if (routed.isEmpty()) {
            // ★ 모델에게 다시 시키지 않는다. 관리자에게 다시 묻는다
            job.askBack("요청을 이해하지 못했습니다. 어느 부분을 어떻게 바꿀지 알려 주세요. "
                    + "(예: 제목을 '가을 대축제' 로 바꿔줘)");
            return null;
        }

        List<Decision.Run> plan = new ArrayList<>(routed.get().size());
        Set<Block> targets = EnumSet.noneOf(Block.class);

        for (RawRoute raw : routed.get()) {
            switch (GATE.decide(raw)) {
                case Decision.Reject r -> {
                    log.info("수정 거절 (event={}, code={}) — {}",
                            cmd.eventId(), r.code().getCode(), r.message());
                    job.fail(r.message());
                    return null;
                }
                case Decision.AskBack a -> {
                    job.askBack(a.question());
                    return null;
                }
                case Decision.Run r -> {
                    // ★ 같은 영역이 두 번 오면 거절한다.
                    //   [DELETE steps, EDIT steps] 같은 조합은 두 번째에서 기준이 사라져
                    //   병합이 터진다. 라우터가 낼 일이 드물고, 제대로 다루면 복잡해진다
                    if (!targets.add(r.block())) {
                        job.fail("같은 부분에 대한 요청이 두 개입니다. 하나씩 말씀해 주세요.");
                        return null;
                    }
                    Decision.Run fixed = againstDocument(job, doc, r);
                    if (fixed == null) return null;     // job 에 문구가 담겼다
                    plan.add(fixed);
                }
            }
        }
        log.info("수정 계획 (event={}, job={}) — {}", cmd.eventId(), job.jobId(),
                plan.stream().map(r -> r.op() + ":" + r.block().key()).toList());
        return plan;
    }

    /**
     * 문서와 대조해 연산을 교정하거나 거절한다.
     *
     * ★ 있는데 ADD → EDIT 으로 고친다
     *
     * ★ 없는데 ADD 가 아니면 거절한다
     *
     */
    private Decision.Run againstDocument(GenerationJob job, String doc, Decision.Run r) {
        boolean present = !BlockValidator.blockOf(doc, r.block()).isBlank();

        if (r.op() == Op.ADD && present) {
            log.info("수정 — {} 는 이미 있어 ADD 를 EDIT 으로 본다 (event={})",
                    r.block().key(), job.eventId());
            return new Decision.Run(r.block(), Op.EDIT, r.content());
        }

        if (r.op() != Op.ADD && !present) {
            log.info("수정 거절 — {} 영역이 문서에 없다 (event={}, op={})",
                    r.block().key(), job.eventId(), r.op());
            job.fail(r.op() == Op.DELETE
                    ? "지우려는 부분이 이미 없습니다."
                    : "고치려는 부분이 페이지에 없습니다. 먼저 추가해 달라고 말씀해 주세요.");
            return null;
        }
        return r;
    }

    // ── 연산 하나 ─────────────────────────────────────────────────

    /**
     * 연산 하나를 문서에 반영한다.
     */
    private String apply(GenerationJob job, EditCommand cmd, String doc, Decision.Run step) {
        Block block = step.block();
        String before = BlockValidator.blockOf(doc, block);

        // ★ 삭제는 모델을 부르지 않는다. 지우는 데 모델이 필요 없다.
        //   canDelete() 가 참인 블록은 steps 하나뿐이다(나머지는 필수)
        if (step.op() == Op.DELETE) {
            // 문서에 있는지는 planAll 의 againstDocument 가 이미 봤다
            log.info("수정 — {} 삭제 (event={}). 모델을 부르지 않는다", block.key(), cmd.eventId());
            return removeBlock(doc, block);
        }

        job.to(GenerationJob.Phase.CALLING);

        // ★ 연산마다 새 묶음이다. 아래 로그가 jobId 와 묶음을 잇는 유일한 끈이다 —
        //   llm_call_logs 에는 상위 요청을 가리킬 컬럼이 없다
        LlmCallContext ctx = LlmCallContext.newGroup(cmd.eventId());
        log.info("수정 — {} {} (event={}, job={}, 호출묶음={})",
                block.key(), step.op(), cmd.eventId(), job.jobId(), ctx.requestId());

        RetryService.Result res;
        try {
            res = retry.run(ctx,
                    PromptBuilder.edit(block),
                    userPrompt(cmd, step, before),
                    HtmlPolicy.edit(block, before, cmd.requestText()));
        } catch (RetryService.Aborted e) {
            // ★ 터진 시도의 실패 행은 문이 남겼다. 그 앞의 시도들은 아직 아무도 안 남겼다.
            //   ctx 가 여기 있으므로 여기서 남긴다 — runSafely 로 올라가면 못 남긴다
            recorder.recordAttempts(ctx, e.partial());
            throw e;
        }

        // ★ 성공이든 실패든 시도 전부를 남긴다. 취소돼도 쓴 토큰은 쓴 것이다
        recorder.recordAttempts(ctx, res);
        job.attempt(res.attempts());

        if (!res.ok()) {
            log.info("수정 실패 — {} 영역, {}회 시도, 마지막 사유 {}",
                    block.key(), res.attempts(), res.lastFailures());
            job.fail("요청을 반영하지 못했습니다. "
                    + "요청을 조금 더 구체적으로 적어 다시 시도해 주세요.");
            return null;
        }
        // ★ 없던 영역이면 병합이 아니라 삽입이다.
        //   BlockMerge.merge 는 있는 섹션을 갈아끼울 뿐 새로 만들지 못한다
        return before.isBlank()
                ? insertBlock(doc, block, res.html().strip())
                : BlockMerge.merge(doc, block, res.html());
    }

    /**
     * 없던 섹션을 문서 순서에 맞는 자리에 끼워 넣는다.
     */
    private static String insertBlock(String doc, Block block, String section) {
        for (Block after : Block.values()) {
            if (after.ordinal() <= block.ordinal()) continue;
            if (BlockValidator.blockOf(doc, after).isBlank()) continue;

            int at = doc.indexOf(BlockMerge.extract(doc, after));
            if (at >= 0) {
                return doc.substring(0, at) + section + "\n" + doc.substring(at);
            }
        }
        return doc + "\n" + section;
    }

    /**
     * 섹션을 문서에서 걷어낸다.
     */
    private static String removeBlock(String doc, Block block) {
        return doc.replace(BlockMerge.extract(doc, block), "");
    }

    // ── 프롬프트 ──────────────────────────────────────────────────

    /**
     * 모델에게 보낼 사용자 메시지.
     *
     * ★ 요청문 <b>전체</b>를 준다. 라우터의 content 만으로는 부족하다
     *
     * ★ 대가 — 다른 영역 얘기가 섞여 들어간다
     *
     * ★ 현재 내용을 준다. 이게 없으면 모델은 무엇을 고칠지 모른다
     */
    private static String userPrompt(EditCommand cmd, Decision.Run step, String before) {
        StringJoiner s = new StringJoiner("\n");
        if (cmd.title() != null && !cmd.title().isBlank()) {
            s.add("이벤트 제목: " + cmd.title().strip());
            s.add("");
        }
        s.add("[관리자 요청]");
        s.add(RequestFilter.clean(cmd.requestText()));
        s.add("");

        if (step.content() != null && !step.content().isBlank()) {
            s.add("[관리자가 직접 쓴 문구 — 이 문구를 그대로 쓴다]");
            s.add(step.content().strip());
            s.add("");
        }

        if (before.isBlank()) {
            s.add("[현재 상태]");
            s.add("이 영역이 아직 없습니다. 새로 만드세요.");
        } else {
            s.add("[현재 내용 — 이걸 고쳐서 전체를 다시 출력한다]");
            s.add(before);
        }
        return s.toString();
    }
}
