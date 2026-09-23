package com.newvent.generation.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 생성 한 건의 진행 상태.
 *
 * ★ **인메모리다. DB 에 안 넣는다.**
 *
 * ★ 서버가 죽으면 이 객체는 사라진다. 그건 의도다.
 *   대신 ChatMessage.status 에 PENDING 이 남으므로
 *   **기동 시 PENDING → FAILED 로 정리**해야 화면이 "처리 중" 에서 끝난다.
 *   ChatMessageStatus 가 PENDING · COMPLETED · FAILED · CANCELLED 라
 *   아래 Phase 와 그대로 대응된다.
 *
 * ★ 스레드 두 개가 본다
 *   생성은 별도 스레드에서 돌고, 관리자는 폴링으로 상태를 읽는다.
 */
public final class GenerationJob {

    /** 진행 단계. **퍼센트를 직접 들고 있지 않는다** — 단계가 곧 퍼센트다 */
    public enum Phase {
        QUEUED   ("대기 중",            0),
        PREPARING("준비 중",           10),
        CALLING  ("페이지를 만드는 중", 40),
        VALIDATING("확인하는 중",       80),
        SAVING   ("저장하는 중",        95),
        DONE     ("완료",             100),
        FAILED   ("실패",             100),
        CANCELLED("중단됨",           100);

        private final String label;
        private final int percent;

        Phase(String label, int percent) { this.label = label; this.percent = percent; }

        public String label()  { return label; }
        public int percent()   { return percent; }
        public boolean done()  { return this == DONE || this == FAILED || this == CANCELLED; }
    }

    /**
     * ★ UUID 다. llm_call_logs.request_id 와 **같은 값을 쓴다.**
     *   그러면 컬럼을 더 두지 않고도 "이 생성이 모델을 몇 번 불렀고 왜 실패했나" 가 이어진다.
     *   호출 로그를 쓸 때 이 값을 request_id 로 넣으면 된다.
     */
    private final UUID jobId = UUID.randomUUID();
    private final Long eventId;
    private final Instant startedAt = Instant.now();

    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.QUEUED);
    private final AtomicReference<String> message = new AtomicReference<>();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

    /**
     * 백지 경로에서 지금 몇 번째 시도인가. 템플릿 경로에서는 0으로 남는다.
     * 관리자에게 "2번째 시도 중" 을 보여주려는 게 아니라 **우리가 로그에서 보려는 것**이다.
     */
    private final AtomicReference<Integer> attempt = new AtomicReference<>(0);

    public GenerationJob(Long eventId) {
        this.eventId = eventId;
    }

    public UUID jobId()        { return jobId; }
    public Long eventId()      { return eventId; }
    public Instant startedAt() { return startedAt; }
    public Phase phase()       { return phase.get(); }
    public int attempt()       { return attempt.get(); }
    public boolean done()      { return phase.get().done(); }

    /** 관리자에게 보여줄 문장. 실패했을 때만 채워진다 */
    public String message()    { return message.get(); }

    /**
     * 단계를 옮긴다. **이미 끝난 작업은 움직이지 않는다.**
     */
    public void to(Phase next) {
        phase.updateAndGet(cur -> cur.done() ? cur : next);
    }

    public void attempt(int n) {
        attempt.set(n);
    }

    public void fail(String userMessage) {
        message.set(userMessage);
        to(Phase.FAILED);
    }


    /**
     * 중단을 요청한다. **즉시 멈추지 않는다.**
     *
     * ★ RetryService 루프 중간에는 끼어들 훅이 없다.
     */
    public void requestCancel() {
        cancelRequested.set(true);
    }

    public boolean cancelRequested() {
        return cancelRequested.get();
    }

    /**
     * 단계 사이에서 부른다. 중단 요청이 있으면 true 를 돌려주고 상태를 CANCELLED 로 만든다.
     * 호출부는 true 면 그 자리에서 return 한다.
     */
    public boolean checkCancelled() {
        if (!cancelRequested.get() || phase.get().done()) return false;

        message.set("생성을 중단했습니다.");
        to(Phase.CANCELLED);
        return true;
    }
}
