package com.newvent.generation.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.EventVersion;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;

/**
 * {@code event_versions} 에 실제로 쓰는 구현.
 *
 * ★ 스스로 트랜잭션을 연다
 *
 * ★ Event 를 getReferenceById 로 받는다
 *
 * ★ version_no 를 max+1 로 매긴다 — 이벤트당 직렬화에 의존한다
 *
 *   새 행을 만드는 경로가 여기 하나인 것은 확인했다 — 저장 지점 지정·해제는 기존 행의
 *   플래그만 바꾸고, 버전 미리보기는 읽기 전용이다.
 *
 * ★ is_checkpoint 는 건드리지 않는다
 *   새 행은 항상 false 다. 관리자가 "저장" 을 누르면 EventVersionService 가 켠다.
 *
 * ★ requestMessage 는 아직 null 이다
 */
@Service
public class JpaVersionStore implements VersionStore {

    private final EventRepository events;
    private final EventVersionRepository versions;

    public JpaVersionStore(EventRepository events, EventVersionRepository versions) {
        this.events = events;
        this.versions = versions;
    }

    @Override
    @Transactional
    public Saved save(Long eventId, String html, Long sourceVersionId) {
        int nextNo = versions.findTopByEventIdOrderByVersionNoDesc(eventId)
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        // ★ 프록시로 받는다 — FK 만 필요하고 내용을 안 읽는다
        EventVersion source = (sourceVersionId == null)
                ? null
                : versions.getReferenceById(sourceVersionId);

        EventVersion saved = versions.save(EventVersion.create(
                events.getReferenceById(eventId), nextNo, html, source));

        return new Saved(saved.getId(), saved.getVersionNo());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> latest(Long eventId) {
        return versions.findTopByEventIdOrderByVersionNoDesc(eventId)
                .map(EventVersion::getHtmlContent);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Saved> latestVersion(Long eventId) {
        return versions.findTopByEventIdOrderByVersionNoDesc(eventId)
                .map(v -> new Saved(v.getId(), v.getVersionNo()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> htmlOf(Long eventId, Long versionId) {
        if (versionId == null) return Optional.empty();

        // ★ eventId 를 같이 건다. 남의 이벤트 버전 id 를 넣어도 안 나온다
        return versions.findByIdAndEventId(versionId, eventId)
                .map(EventVersion::getHtmlContent);
    }
}
