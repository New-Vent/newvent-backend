package com.newvent.generation.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.newvent.common.exception.code.ErrorCode;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.infra.llm.LlmCallException;
import com.newvent.registry.Block;
import com.newvent.registry.PromptBuilder;
import com.newvent.registry.Slot;
import com.newvent.registry.Slots;

/**
 * 관리자의 생성 요청 하나를 **페이지 첫 버전**까지 끌고 간다.
 *
 * ★ 경로가 둘이고 비용이 완전히 다르다
 *     템플릿 — 파일에서 읽어 슬롯만 비운다. **모델을 아예 안 부른다.** 밀리초
 *     백지   — 프롬프트 → 재시도 루프(최대 4회) → 기간 슬롯 심기. 수 초~수십 초
 *   진행률을 한 벌로 만들면 템플릿 경로가 어색해지므로, 단계만 같이 쓰고 내용은 다르다.
 *
 * ★ 실패는 실패로 끝난다
 *   4회 다 실패해도 기본 템플릿을 끼워 넣지 않는다.
 *   관리자가 요청하지 않은 페이지가 조용히 저장되는 게 더 나쁘다.
 *
 * ★ 내부 오류 원문을 관리자에게 보여주지 않는다
 *   "Connection refused: localhost:11434" 는 로그에만 남기고,
 *   화면에는 "생성 서버에 연결하지 못했습니다" 가 간다.
 */
@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private final RetryService retry;
    private final TemplateService templates;
    private final VersionStore versions;
    private final EventGuard guard;
    private final GenerationJobStore jobs;


    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "generation");
        t.setDaemon(true);
        return t;
    });

    public GenerationService(RetryService retry, TemplateService templates,
                             VersionStore versions, EventGuard guard, GenerationJobStore jobs) {
        this.retry = retry;
        this.templates = templates;
        this.versions = versions;
        this.guard = guard;
        this.jobs = jobs;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    // ── 시작 ──────────────────────────────────────────────────────

    /** 시작 결과. 거부도 정상 흐름이라 예외로 던지지 않는다 */
    public sealed interface StartResult {
        record Started(GenerationJob job) implements StartResult {}

        /** 요청 자체가 잘못됐다. 컨트롤러가 GenerationException 으로 바꿔 던진다 */
        record Rejected(ErrorCode errorCode) implements StartResult {}

        /** 이미 돌고 있다 — 409. 진행 중인 작업을 같이 준다 */
        record AlreadyRunning(GenerationJob job) implements StartResult {}
    }

    /**
     * 검사만 하고 바로 돌아온다. **실제 생성은 다른 스레드에서 돈다.**
     *
     * ★ 검사 순서가 중요하다 — 자리를 잡기 전에 거를 것을 다 거른다.
     *   자리를 먼저 잡으면 거부된 요청이 그 이벤트의 생성을 잠시 막는다.
     */
    public StartResult start(GenerateCommand cmd) {
        // ① 이벤트를 건드려도 되는가
        Optional<ErrorCode> blocked = guard.rejectReason(cmd.eventId());
        if (blocked.isPresent()) {
            return new StartResult.Rejected(blocked.get());
        }

        // ② 경로별 검사
        if (cmd.hasTemplate()) {

            if (!templates.exists(cmd.templateCode())) {
                log.warn("없는 템플릿 코드로 생성 요청 (event={}, code={}). "
                        + "events.template_id 와 템플릿 저장소가 어긋났을 수 있습니다.",
                        cmd.eventId(), cmd.templateCode());
                return new StartResult.Rejected(GenerationErrorCode.TEMPLATE_NOT_FOUND);
            }
        } else {
            // 입력 필터  — 백지 생성일 때만. 템플릿 경로는 요청문이 필요 없다
            Optional<GenerationErrorCode> bad = RequestFilter.reject(cmd.requestText());
            if (bad.isPresent()) {
                return new StartResult.Rejected(bad.get());
            }
        }

        // ③ 자리 잡기
        Optional<GenerationJob> slot = jobs.start(cmd.eventId());
        if (slot.isEmpty()) {
            return new StartResult.AlreadyRunning(
                    jobs.ofEvent(cmd.eventId()).orElseThrow());
        }

        GenerationJob job = slot.get();
        worker.submit(() -> runSafely(job, cmd));
        return new StartResult.Started(job);
    }

    /** 중단 요청 — 즉시 멈추지 않는다. 단계 사이에서 멈춘다 (REQ-LLM-34) */
    public boolean cancel(Long eventId) {
        return jobs.ofEvent(eventId)
                .map(j -> { j.requestCancel(); return true; })
                .orElse(false);
    }

    // ── 실행 ──────────────────────────────────────────────────────

    /**
     * ★ 여기서 모든 예외를 잡는다.
     */
    private void runSafely(GenerationJob job, GenerateCommand cmd) {
        try {
            run(job, cmd);
        } catch (LlmCallException e) {
            // 연결 끊김 · 타임아웃 · 모델 서버 down. 프롬프트를 고쳐도 안 고쳐진다
            log.warn("생성 실패 — 모델 호출 (event={})", cmd.eventId(), e);
            job.fail("페이지 생성 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (Exception e) {
            log.error("생성 실패 — 예상치 못한 오류 (event={})", cmd.eventId(), e);
            job.fail("페이지를 만드는 중 문제가 생겼습니다. 잠시 후 다시 시도해 주세요.");
        } finally {
            // ★ 반드시 비운다. 안 비우면 그 이벤트는 다음 생성을 영원히 못 한다
            jobs.finish(job);
        }
    }

    private void run(GenerationJob job, GenerateCommand cmd) {
        job.to(GenerationJob.Phase.PREPARING);
        if (job.checkCancelled()) return;

        String html = cmd.hasTemplate()
                ? fromTemplate(job, cmd)
                : fromBlank(job, cmd);

        if (html == null) return;
        if (job.checkCancelled()) return;

        job.to(GenerationJob.Phase.SAVING);
        String note = cmd.hasTemplate()
                ? "템플릿 " + cmd.templateCode()
                : "백지 생성";
        versions.save(cmd.eventId(), html, note);

        job.to(GenerationJob.Phase.DONE);
    }

    // ── 경로 ① 템플릿 ────────────────────────────────────────────

    /**
     * 모델을 안 부른다. 재정화 → 슬롯 비우기까지가 전부다.
     *
     * ★ 슬롯을 비운 상태로 저장한다. 값은 보여줄 때 채운다.
     *   저장할 때 채우면 그 날짜가 굳어서, 이벤트에서 기간만 고쳐도 화면이 안 바뀐다.
     */
    private String fromTemplate(GenerationJob job, GenerateCommand cmd) {
        job.to(GenerationJob.Phase.VALIDATING);
        return templates.initialHtml(cmd.templateCode());
    }

    // ── 경로 ② 백지 ──────────────────────────────────────────────

    private String fromBlank(GenerationJob job, GenerateCommand cmd) {
        job.to(GenerationJob.Phase.CALLING);

        RetryService.Result res = retry.run(
                PromptBuilder.generate(),
                userPrompt(cmd),
                HtmlPolicy.generation());

        job.attempt(res.attempts());
        if (job.checkCancelled()) return null;

        job.to(GenerationJob.Phase.VALIDATING);

        if (!res.ok()) {
            // ★ 여기서 기본 템플릿을 끼워 넣지 않는다 (REQ-LLM-44 뒤집기)
            log.info("생성 실패 — {}회 시도, 마지막 사유 {}",
                    res.attempts(), res.lastFailures());
            job.fail("요청을 반영한 페이지를 만들지 못했습니다. "
                    + "요청을 조금 더 구체적으로 적어 다시 시도해 주세요.");
            return null;
        }
        checkFormValues(cmd, res.html());
        return plantPeriodSlot(res.html());
    }

    /**
     * 이벤트 값과 어긋나는지 본다.
     *
     *   **이 로그가 곧 측정이다.** 한 주 동안 얼마나 자주 나는지 보고,
     *   흔하면 프롬프트를 고치고 드물면 검증으로 올린다.
     *   올리는 건 HtmlPolicy.generation() 에 한 줄 더하는 일이다.
     */
    private void checkFormValues(GenerateCommand cmd, String html) {
        List<String> dates = FormValueCheck.leakedDates(html);
        if (dates.isEmpty()) return;

        // ★ WARN 이다. 찾으려면 눈에 띄어야 한다
        log.warn("REQ-LLM-41 — 본문에 지어낸 날짜 {}개 (event={}): {}. "
                + "기간은 슬롯으로만 들어가야 합니다. 프롬프트에는 날짜를 주지 않았습니다.",
                dates.size(), cmd.eventId(), dates);
    }

    /**
     * 백지 결과에 기간 슬롯을 심는다.
     */
    private String plantPeriodSlot(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);

        if (!doc.body().select(Slot.PERIOD.selector()).isEmpty()) {
            return doc.body().html();      // 이미 있으면 두 번 심지 않는다
        }

        Element hero = doc.body().selectFirst(Block.HERO.selector());
        Element host = (hero != null) ? hero : doc.body();

        // ★ 내용은 비워 둔다. 값은 보여줄 때 Slots.fill() 이 채운다
        host.appendElement("p").attr("data-slot", Slot.PERIOD.key());

        return doc.body().html();
    }

    // ── 프롬프트 ──────────────────────────────────────────────────

    /**
     * 모델에게 보낼 사용자 메시지.
     *
     * ★ 기간을 **주지 않는다.**
     *   기간은 슬롯 하나로만 존재해야 한다.
     *
     * ★ 참여 링크도 안 준다. 같은 이유이고, CTA 는 문구만 만들면 된다.
     */
    private String userPrompt(GenerateCommand cmd) {
        StringBuilder s = new StringBuilder();
        if (cmd.title() != null && !cmd.title().isBlank()) {
            s.append("이벤트 제목: ").append(cmd.title().strip()).append("\n\n");
        }
        s.append(RequestFilter.clean(cmd.requestText()));
        return s.toString();
    }

    // ── 보여주기 ──────────────────────────────────────────────────

    /**
     * 저장된 HTML 에 이벤트 값을 채워 돌려준다. **미리보기·게시 응답에서 부른다.**
     *
     * ★ 저장 경로에서 부르면 안 된다. 값이 박혀서 들어가고 clear() 한 이유가 없어진다.
     */
    public Optional<String> render(GenerateCommand cmd) {
        return versions.latest(cmd.eventId())
                .map(html -> Slots.fill(html, cmd.period(), cmd.ctaUrl()));
    }
}
