package com.newvent.generation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 진행 중인 생성을 들고 있는 곳. **중복 요청을 여기서 막는다.
 *
 * ★ 인메모리로 확정.
 */
@Component
public class GenerationJobStore {

    /** 끝난 작업을 언제까지 조회할 수 있게 둘까. 관리자가 결과를 읽을 시간이면 충분하다 */
    private static final Duration KEEP = Duration.ofMinutes(30);

    private final Map<Long, GenerationJob> running = new ConcurrentHashMap<>();
    private final Map<UUID, GenerationJob> byId = new ConcurrentHashMap<>();

    /**
     * 자리를 잡는다. **이미 돌고 있으면 빈 값** — 호출부는 그걸 보고 거부한다.
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
     */
    private void sweep() {
        Instant cutoff = Instant.now().minus(KEEP);
        byId.values().removeIf(j -> j.done() && j.startedAt().isBefore(cutoff));
    }
}
