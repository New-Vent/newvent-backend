package com.newvent.generation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 진행 중인 생성을 들고 있는 곳. **중복 요청을 여기서 막는다.** `REQ-LLM-29`·`30`
 *
 * ★ 인메모리로 확정. **DB 에 안 넣는다.**
 *   팀 경계가 "기록은 유동 · 진행 상태는 지원" 이다.
 *   끝난 결과는 EventVersion · LlmCallLog 로 가고, "지금 몇 % 인가" 는 여기서만 산다.
 *   실제로 generation/domain 에 GenerationJob 엔티티가 없고 V9 generation_job 도 없다.
 *
 *   진행 상태가 살아 있는 시간은 수 초~수십 초다. 그 사이에 서버가 죽는 경우를 위해
 *   단계마다 UPDATE 를 날리는 값은 크다.
 *
 * ★ 따라오는 제약 두 가지 — 알고 가는 것이다
 *   ① 서버를 재시작하면 진행 중이던 작업은 사라진다.
 *      그런데 ChatMessage.status 에는 PENDING 이 남으므로
 *      **기동 시 PENDING → FAILED 로 정리**해야 화면이 "처리 중" 에서 끝난다.
 *   ② **서버가 한 대라는 전제다.** 두 대로 늘리면 A 에서 시작한 작업을
 *      B 가 폴링할 때 못 찾는다. 늘릴 계획이 생기면 그때 다시 본다.
 *
 * ★ 왜 이벤트당 1개인가
 *   같은 이벤트에 생성이 둘 돌면 둘 다 첫 버전을 저장하려 든다.
 *   버전 번호가 어긋나고, 관리자는 자기가 안 만든 페이지를 본다. 그리고 GPU 가 하나다.
 *
 * ★ 맵이 두 개인 이유
 *   running   이벤트당 1개 — 중복 막기용. 끝나면 즉시 빠진다
 *   byId      jobId 로 조회 — 끝난 뒤에도 결과를 읽어야 한다
 *   하나로 합치면 "끝난 작업이 남아서 새 생성이 거부되는" 문제가 생긴다.
 */
@Component
public class GenerationJobStore {

    /** 끝난 작업을 언제까지 조회할 수 있게 둘까. 관리자가 결과를 읽을 시간이면 충분하다 */
    private static final Duration KEEP = Duration.ofMinutes(30);

    private final Map<Long, GenerationJob> running = new ConcurrentHashMap<>();
    private final Map<UUID, GenerationJob> byId = new ConcurrentHashMap<>();

    /**
     * 자리를 잡는다. **이미 돌고 있으면 빈 값** — 호출부는 그걸 보고 거부한다.
     *
     * ★ computeIfAbsent 로 원자적으로 처리한다.
     *   containsKey → put 으로 쓰면 두 요청이 동시에 들어올 때 둘 다 통과한다.
     *   버튼 두 번 누르기가 정확히 그 상황이다.
     */
    public Optional<GenerationJob> start(Long eventId) {
        sweep();

        GenerationJob created = new GenerationJob(eventId);
        GenerationJob winner = running.computeIfAbsent(eventId, id -> created);
        if (winner != created) return Optional.empty();   // 남이 먼저 잡았다

        byId.put(created.jobId(), created);
        return Optional.of(created);
    }

    /**
     * 자리를 비운다. **끝나면 반드시 부른다 — finally 에서.**
     * 안 부르면 그 이벤트는 다음 생성을 영원히 못 한다.
     */
    public void finish(GenerationJob job) {
        running.remove(job.eventId(), job);
    }

    /** 진행 중인 것. 중단 요청이 이걸 찾는다 */
    public Optional<GenerationJob> ofEvent(Long eventId) {
        return Optional.ofNullable(running.get(eventId));
    }

    /** 끝난 것도 포함. 상태 조회가 이걸 찾는다 */
    public Optional<GenerationJob> byId(UUID jobId) {
        return jobId == null ? Optional.empty() : Optional.ofNullable(byId.get(jobId));
    }

    /**
     * 경로 변수처럼 **글자로 들어온 것**을 찾는다.
     *
     * ★ 형식이 UUID 가 아니면 예외가 아니라 빈 값이다.
     *   /generate/abc 같은 주소는 사고가 아니라 오타다. 500 이 아니라 404 로 가야 한다.
     */
    public Optional<GenerationJob> byId(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return byId(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 오래된 것을 치운다. **인메모리 맵은 안 치우면 계속 자란다.**
     *
     * ★ 스케줄러를 안 쓰고 start() 에서 부른다.
     *   생성이 일어날 때만 청소하면 충분하고, @Scheduled 를 하나 늘리는 것보다 단순하다.
     *   생성이 아예 안 일어나면 맵도 안 자라므로 청소할 이유가 없다.
     */
    private void sweep() {
        Instant cutoff = Instant.now().minus(KEEP);
        byId.values().removeIf(j -> j.done() && j.startedAt().isBefore(cutoff));
    }
}
