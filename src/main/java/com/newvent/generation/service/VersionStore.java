package com.newvent.generation.service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 완성된 HTML 을 이벤트의 버전으로 남기는 자리.
 *
 * ★ 저장 결과로 id 를 돌려준다
 *   수정은 "어느 버전을 고쳐서 나온 버전" 이다. 그걸 표현하려면
 *   {@code event_versions.source_version_id} 에 넣을 id 가 필요하고,
 *   그 id 는 저장한 쪽만 안다.
 *
 * ★ sourceNote 를 뺐다
 *   {@code event_versions} 에 그 문자열을 넣을 컬럼이 없다. 인메모리 구현도 무시하고 있었다.
 *   "템플릿 T1" / "백지 생성" 은 로그로 남긴다. 저장이 필요해지면 컬럼이 먼저 생겨야 한다.
 */
public interface VersionStore {

    /**
     * 저장된 버전의 신분.
     *
     * ★ versionNo 는 이벤트 안에서의 순번이다. id 와 다르다.
     *   화면에 "v3" 으로 보이는 건 versionNo 이고, 참조에 쓰는 건 id 다.
     */
    record Saved(Long versionId, int versionNo) {}

    /**
     * 새 버전으로 남긴다.
     */
    Saved save(Long eventId, String html, Long sourceVersionId);

    /** 마지막 버전의 HTML. 없으면 빈 값 */
    Optional<String> latest(Long eventId);

    /**
     * 마지막 버전의 신분. <b>수정이 sourceVersionId 를 채우려면 이걸 먼저 부른다.</b>
     */
    Optional<Saved> latestVersion(Long eventId);

    /**
     * 특정 버전의 HTML.
     *
     */
    Optional<String> htmlOf(Long eventId, Long versionId);

    /**
     * DB 를 띄우지 않는 테스트용. <b>빈이 아니다 — 테스트가 직접 만든다.</b>
     */
    class InMemory implements VersionStore {

        private final Map<Long, String> latestHtml = new ConcurrentHashMap<>();
        private final Map<Long, Saved> lastSaved = new ConcurrentHashMap<>();
        private final Map<Long, String> htmlById = new ConcurrentHashMap<>();
        private final Map<Long, Long> eventOfVersion = new ConcurrentHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Saved save(Long eventId, String html, Long sourceVersionId) {
            Saved prev = lastSaved.get(eventId);
            Saved now = new Saved(ids.incrementAndGet(),
                    prev == null ? 1 : prev.versionNo() + 1);

            latestHtml.put(eventId, html);
            lastSaved.put(eventId, now);
            htmlById.put(now.versionId(), html);
            eventOfVersion.put(now.versionId(), eventId);
            return now;
        }

        @Override
        public Optional<String> latest(Long eventId) {
            return Optional.ofNullable(latestHtml.get(eventId));
        }

        @Override
        public Optional<Saved> latestVersion(Long eventId) {
            return Optional.ofNullable(lastSaved.get(eventId));
        }

        @Override
        public Optional<String> htmlOf(Long eventId, Long versionId) {
            if (!Objects.equals(eventOfVersion.get(versionId), eventId)) {
                return Optional.empty();   // 남의 이벤트 버전은 안 준다
            }
            return Optional.ofNullable(htmlById.get(versionId));
        }
    }
}
