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

        /**
         * 수정에서만 쓴다 — 라우터가 "무엇을 어디에" 를 정하는 동안.
         * 생성은 이 단계를 지나지 않는다.
         */
        ROUTING  ("요청을 이해하는 중", 20),

        CALLING  ("페이지를 만드는 중", 40),
        VALIDATING("확인하는 중",       80),
        SAVING   ("저장하는 중",        95),
        DONE     ("완료",             100),
        FAILED   ("실패",             100),

        /**
         * 되묻기. <b>실패가 아니다</b> — 관리자가 답만 하면 된다.
         *
         * ★ FAILED 로 보내면 안 되는 이유
         *   화면에 빨간 "실패" 가 뜬다. 관리자는 잘못한 게 없다.
         *   라우터가 못 알아들었거나, 항목 값을 관리자가 정해야 하는 경우다.
         *
         * ★ done() 에 <b>넣어야</b> 한다
         *   안 넣으면 프론트가 영원히 폴링한다. 이 작업은 여기서 끝이고,
         *   관리자의 답은 새 요청이다.
         */
        ASK_BACK ("확인 필요",         100),

        CANCELLED("중단됨",           100);

        private final String label;
        private final int percent;

        Phase(String label, int percent) { this.label = label; this.percent = percent; }

        public String label()  { return label; }
        public int percent()   { return percent; }
        public boolean done()  {
            return this == DONE || this == FAILED || this == ASK_BACK || this == CANCELLED;
        }
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

    /**
     * 이 작업이 만들어 낸 버전의 id. <b>저장 단계를 지나기 전에는 null</b>.
     *
     * ★ 실패 · 중단으로 끝나면 끝까지 null 이다 — 저장 단계에 도달하지 못했으므로 버전이 없다.
     */
    private final AtomicReference<Long> versionId = new AtomicReference<>();
    public record PrivacyRequest(String flow, Long baseVersionId) {}
    private volatile PrivacyRequest privacyRequest;
    private volatile String privacyFingerprint;
    private volatile java.util.List<String> privacyTypes = java.util.List.of();

    public PrivacyRequest privacyRequest() { return privacyRequest; }
    public boolean privacyConfirmationRequired() { return privacyFingerprint != null; }
    public String privacyFingerprint() { return privacyFingerprint; }
    public java.util.List<String> privacyTypes() { return privacyTypes; }
    public void privacyConfirmation(String fingerprint, java.util.List<String> types) {
        privacyTypes = java.util.List.copyOf(types);
        privacyFingerprint = fingerprint;
    }
    public void privacyRequest(String flow, Long baseVersionId) {
        privacyRequest = new PrivacyRequest(flow, baseVersionId);
    }

    public GenerationJob(Long eventId) {
        this.eventId = eventId;
    }

    public UUID jobId()        { return jobId; }
    public Long eventId()      { return eventId; }
    public Instant startedAt() { return startedAt; }
    public Phase phase()       { return phase.get(); }
    public int attempt()       { return attempt.get(); }
    public boolean done()      { return phase.get().done(); }

    /** 이 작업이 만든 버전의 id. 저장 전에는 null */
    public Long versionId()    { return versionId.get(); }

    /**
     * 관리자에게 보여줄 문장.
     *
     * ★ 실패 문구이거나 되묻기 질문이다. 어느 쪽인지는 phase 가 말한다 —
     *   FAILED · CANCELLED 면 사유, ASK_BACK 이면 질문이다. 그 밖에는 null 이다.
     */
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

    /**
     * 저장 직후 워커가 부른다. 한 작업에서 한 번만 불린다.
     *
     * ★★ <b>to(DONE) 보다 먼저 불러야 한다.</b>
     *   순서를 뒤집으면 폴링이 phase == DONE 을 보고 versionId 를 읽었는데
     *   아직 null 인 창이 생긴다. 먼저 쓰면 그런 창이 없다.
     */
    public void versionId(Long id) {
        versionId.set(id);
    }

    /**
     * 관리자에게 되묻고 끝낸다. <b>실패로 기록하지 않는다.</b>
     *
     * ★ 질문을 message 에 담는다 — 필드를 새로 두지 않았다
     *   프론트는 phase 로 갈라 읽는다. FAILED 면 오류 문구, ASK_BACK 이면 질문이다.
     *   담는 자리가 같아도 뜻이 갈리는 건 phase 가 이미 말해 준다.
     */
    public void askBack(String question) {
        message.set(question);
        to(Phase.ASK_BACK);
    }

    public void fail(String userMessage) {
        message.set(userMessage);
        to(Phase.FAILED);
    }


    /**
     * 중단을 요청한다. **즉시 멈추지 않는다.**
     *
     * ★ RetryService 루프 중간에는 끼어들 훅이 없다.
     * ★ 저장이 시작된 뒤의 중단은 반영되지 않는다 — 저장 후에는 checkCancelled 를
     *   부르지 않으므로 DONE 으로 끝나고 버전 1행이 남는다. 그게 맞다. 이미 저장됐다.
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
