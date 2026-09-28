package com.newvent.generation.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 완성된 HTML 을 이벤트의 버전으로 남기는 자리.
 */
public interface VersionStore {

    /**
     * 새 버전으로 남긴다.
     */
    String save(Long eventId, String html, String sourceNote);

    /** 마지막 버전의 HTML. 없으면 빈 값 */
    Optional<String> latest(Long eventId);

    /**
     * 정본이 정해지기 전까지 쓰는 임시 구현. **DB 구현체가 생기면 지운다.**
     *
     * ★ 서버가 죽으면 다 사라진다. 시연용으로는 못 쓴다.
     * ★ 여기서 매기는 순번은 진짜 version_no 가 아니다. 화면 확인용이다.
     */
    @Component
    class InMemory implements VersionStore {

        private final Map<Long, String> latest = new ConcurrentHashMap<>();
        private final Map<Long, Integer> versionNo = new ConcurrentHashMap<>();

        @Override
        public String save(Long eventId, String html, String sourceNote) {
            int no = versionNo.merge(eventId, 1, Integer::sum);
            latest.put(eventId, html);
            return eventId + "-v" + no;
        }

        @Override
        public Optional<String> latest(Long eventId) {
            return Optional.ofNullable(latest.get(eventId));
        }
    }
}
