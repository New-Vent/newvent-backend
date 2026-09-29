package com.newvent.generation.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.newvent.event.domain.EventVersion;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;

/**
 * {@code event_versions} 에 실제로 쓰는 구현. <b>이게 들어오면서 생성 결과가 서버 재시작을 넘긴다.</b>
 *
 * ★ 스스로 트랜잭션을 연다
 *
 * ★ Event 를 getReferenceById 로 받는다
 *   FK 값만 필요하니 조회가 안 나간다. 대신 영속성 컨텍스트가 있어야 하므로
 *   <b>이 트랜잭션 안</b>에서 불러야 한다.
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
 *   {@code chat_messages} 에 쓰는 사람이 없다. 그 배선이 생기면 인자가 하나 늘어난다.
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

        // ★ eventId 를 같이 건다 — 남의 이벤트 버전을 원본으로 걸 수 없다.
        EventVersion source = (sourceVersionId == null) ? null
                : versions.findByIdAndEventId(sourceVersionId, eventId)
                        .orElseThrow(() -> new EventException(EventErrorCode.VERSION_NOT_FOUND));

        EventVersion saved = versions.save(EventVersion.create(
                events.getReferenceById(eventId), nextNo, html, source));

        return new Saved(saved.getId(), saved.getVersionNo());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Snapshot> latest(Long eventId) {
        // ★ 쿼리 한 번으로 id·번호·HTML 을 같이 꺼낸다.
        //   따로 부르면 그 사이에 새 버전이 저장되어 짝이 어긋날 수 있다.
        return versions.findTopByEventIdOrderByVersionNoDesc(eventId)
                .map(v -> new Snapshot(v.getId(), v.getVersionNo(), v.getHtmlContent()));
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
