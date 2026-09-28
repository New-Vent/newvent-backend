package com.newvent.generation.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.generation.exception.GenerationErrorCode;

/**
 * 진행 상태와 중복 방지. **스프링도 DB 도 모델도 안 띄운다.**
 */
class GenerationJobTest {

    // ── 진행 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("단계가 곧 퍼센트다")
    void 단계가_퍼센트다() {
        GenerationJob job = new GenerationJob(1L);

        assertEquals(0, job.phase().percent());
        assertFalse(job.done());

        job.to(GenerationJob.Phase.CALLING);
        assertEquals(40, job.phase().percent());

        job.to(GenerationJob.Phase.DONE);
        assertEquals(100, job.phase().percent());
        assertTrue(job.done());
    }

    @Test
    @DisplayName("★ 끝난 작업은 되돌아가지 않는다")
    void 끝나면_안_움직인다() {
        GenerationJob job = new GenerationJob(1L);
        job.fail("모델이 응답하지 않습니다.");

        job.to(GenerationJob.Phase.CALLING);   // 늦게 도착한 진행 보고

        assertEquals(GenerationJob.Phase.FAILED, job.phase(),
                "실패한 작업이 다시 진행 중으로 바뀌었습니다. "
                + "중단 직후 생성 스레드가 한 단계 더 진행하는 경우가 실제로 있습니다.");
        assertEquals("모델이 응답하지 않습니다.", job.message());
    }

    // ── 중단 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("중단은 요청하고, 단계 사이에서 멈춘다")
    void 중단() {
        GenerationJob job = new GenerationJob(1L);
        job.to(GenerationJob.Phase.CALLING);

        assertFalse(job.checkCancelled(), "요청도 안 했는데 멈췄습니다.");

        job.requestCancel();

        assertTrue(job.checkCancelled(), "중단 요청을 무시했습니다.");
        assertEquals(GenerationJob.Phase.CANCELLED, job.phase());
        assertTrue(job.done());
    }

    @Test
    @DisplayName("★ 이미 끝난 작업에는 중단이 안 걸린다")
    void 끝난_뒤_중단() {
        GenerationJob job = new GenerationJob(1L);
        job.to(GenerationJob.Phase.DONE);

        job.requestCancel();

        assertFalse(job.checkCancelled(),
                "완료된 작업이 중단으로 바뀌었습니다. 페이지는 이미 저장됐는데 "
                + "화면에는 '중단됨' 이 뜹니다.");
        assertEquals(GenerationJob.Phase.DONE, job.phase());
    }

    // ── 중복 방지 ── `REQ-LLM-29`·`30` ───────────────────────────

    @Test
    @DisplayName("같은 이벤트에 두 번째 생성은 거부된다")
    void 중복_거부() {
        GenerationJobStore store = new GenerationJobStore();

        assertTrue(store.start(7L).isPresent());
        assertTrue(store.start(7L).isEmpty(), "같은 이벤트에 생성이 둘 돌게 됐습니다.");
        assertTrue(store.start(8L).isPresent(), "다른 이벤트까지 막았습니다.");
    }

    @Test
    @DisplayName("끝나면 다시 생성할 수 있다")
    void 끝나면_다시() {
        GenerationJobStore store = new GenerationJobStore();
        GenerationJob first = store.start(7L).orElseThrow();

        first.fail("실패");
        store.finish(first);

        assertTrue(store.start(7L).isPresent(),
                "finish() 후에도 막혀 있습니다. 그 이벤트는 영영 생성을 못 합니다.");
    }

    @Test
    @DisplayName("끝난 작업도 jobId 로는 조회된다")
    void 끝나도_조회된다() {
        GenerationJobStore store = new GenerationJobStore();
        GenerationJob job = store.start(7L).orElseThrow();
        job.fail("모델이 응답하지 않습니다.");
        store.finish(job);

        GenerationJob found = store.byId(job.jobId()).orElseThrow(
                () -> new AssertionError("끝나자마자 조회가 안 됩니다. 관리자가 결과를 못 봅니다."));
        assertEquals("모델이 응답하지 않습니다.", found.message());
        assertTrue(store.ofEvent(7L).isEmpty(), "진행 중 목록에 남아 있습니다.");
    }

    @Test
    @DisplayName("★ 버튼을 20번 동시에 눌러도 하나만 통과한다")
    void 동시에_눌러도_하나() throws Exception {
        GenerationJobStore store = new GenerationJobStore();
        int n = 20;

        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Optional<GenerationJob>>> futures = IntStream.range(0, n)
                    .mapToObj(i -> pool.submit(() -> {
                        go.await();
                        return store.start(7L);
                    }))
                    .toList();

            go.countDown();

            long won = 0;
            for (Future<Optional<GenerationJob>> f : futures) {
                if (f.get(5, TimeUnit.SECONDS).isPresent()) won++;
            }
            assertEquals(1, won,
                    "동시 요청 " + n + "개 중 " + won + "개가 통과했습니다. "
                    + "containsKey → put 으로 쓰면 이렇게 됩니다.");
        }
    }

    // ── jobId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("★ jobId 는 UUID 다 — llm_call_logs.request_id 와 같은 값을 쓴다")
    void jobId는_UUID다() {
        GenerationJobStore store = new GenerationJobStore();
        GenerationJob job = store.start(7L).orElseThrow();

        assertNotNull(job.jobId());
        assertTrue(store.byId(job.jobId()).isPresent(), "UUID 로 못 찾습니다.");
        assertTrue(store.byId(job.jobId().toString()).isPresent(),
                "경로 변수처럼 글자로 들어온 것을 못 찾습니다.");
    }

    @Test
    @DisplayName("UUID 가 아닌 글자는 예외가 아니라 빈 값이다")
    void 이상한_jobId() {
        GenerationJobStore store = new GenerationJobStore();

        assertTrue(store.byId("abc").isEmpty(),
                "/generate/abc 같은 오타에 예외가 나면 404 가 아니라 500 이 됩니다.");
        assertTrue(store.byId((String) null).isEmpty());
        assertTrue(store.byId((java.util.UUID) null).isEmpty());
    }

    // ── 입력 필터 ── `REQ-LLM-16` ────────────────────────────────

    @Test
    @DisplayName("빈 요청과 너무 긴 요청은 거부한다")
    void 입력_필터() {
        assertEquals(GenerationErrorCode.EMPTY_REQUEST, RequestFilter.reject(null).orElseThrow());
        assertEquals(GenerationErrorCode.EMPTY_REQUEST, RequestFilter.reject("   ").orElseThrow());
        assertTrue(RequestFilter.reject("여름 데이터 이벤트 만들어줘").isEmpty());

        String tooLong = "가".repeat(RequestFilter.MAX_LENGTH + 1);
        assertEquals(GenerationErrorCode.REQUEST_TOO_LONG,
                RequestFilter.reject(tooLong).orElseThrow());
    }

    @Test
    @DisplayName("★ 거부 문구에 실제 한도가 들어 있다 — 상수를 바꾸면 문구도 따라간다")
    void 한도가_문구에_들어있다() {
        assertTrue(GenerationErrorCode.REQUEST_TOO_LONG.getMessage()
                        .contains(String.valueOf(RequestFilter.MAX_LENGTH)),
                "관리자가 얼마나 줄여야 할지 모릅니다: "
                + GenerationErrorCode.REQUEST_TOO_LONG.getMessage());
    }

    @Test
    @DisplayName("제어문자와 폭 없는 공백은 털어낸다")
    void 다듬기() {
        assertEquals("여름 이벤트", RequestFilter.clean("여름\u0000 이벤트​"));
        assertEquals("가\n\n나", RequestFilter.clean("가\n\n\n\n나"));
        assertEquals("가\n나", RequestFilter.clean("  가\n나  "), "줄바꿈은 살려야 합니다.");
    }

    @Test
    @DisplayName("★ 눈에 안 보이는 글자만 잔뜩 보내면 거부한다")
    void 보이지_않는_글자() {
        assertTrue(RequestFilter.reject("​​​").isPresent(),
                "폭 없는 공백만 보냈는데 통과했습니다. 빈 요청으로 모델을 부르게 됩니다.");
    }
}
