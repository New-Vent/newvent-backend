package com.newvent.event.repository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.newvent.admin.domain.Admin;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
import com.newvent.user.domain.MembershipGrade;

/**
 * JPA 연동 전 조회·생성 API 용 저장소.
 * 도메인은 develop Entity 를 쓰고, 저장만 메모리로 한다.
 */
@Repository
public class InMemoryEventRepository implements EventRepository {

    private final Map<Long, Event> events = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final Admin systemAdmin = Admin.systemStub();

    public InMemoryEventRepository() {
        seed();
        long maxId = events.keySet().stream().mapToLong(Long::longValue).max().orElse(0L);
        sequence.set(maxId);
    }

    public Admin systemAdmin() {
        return systemAdmin;
    }

    @Override
    public List<Event> findAll() {
        return events.values().stream()
                .sorted(Comparator.comparing(Event::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Event::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public Optional<Event> findById(Long id) {
        return Optional.ofNullable(events.get(id));
    }

    @Override
    public Event save(Event event) {
        Long id = event.getId();
        if (id == null) {
            id = sequence.incrementAndGet();
            event.assignId(id);
            if (event.getUpdatedAt() == null) {
                event.touchUpdatedAt(OffsetDateTime.now());
            }
        } else {
            sequence.accumulateAndGet(id, Math::max);
        }
        events.put(id, event);
        return event;
    }

    private void seed() {
        save(reconstitute(1L, "신규 가입 데이터 쿠폰 3GB", EventStatus.PUBLISHED,
                "2026-09-16T00:00:00+09:00", "2026-10-15T23:59:59+09:00",
                "2026-09-16T10:20:00+09:00", null, MembershipGrade.NORMAL,
                html("<section data-block=\"hero\"><h1>신규 가입 데이터 쿠폰 3GB</h1></section>")));
        save(reconstitute(2L, "월드컵 스코어 맞추기", EventStatus.DRAFT,
                "2026-07-01T00:00:00+09:00", "2026-07-31T23:59:59+09:00",
                "2026-06-28T16:00:00+09:00", null, MembershipGrade.EXCELLENT, null));
        save(reconstitute(3L, "지금 긁으면 바로 당첨", EventStatus.PUBLISHED,
                "2026-09-16T00:00:00+09:00", "2026-09-18T23:59:59+09:00",
                "2026-09-15T09:10:00+09:00", null, MembershipGrade.NORMAL,
                html("<section data-block=\"hero\"><h1>지금 긁으면 바로 당첨</h1></section>")));
        save(reconstitute(4L, "가을 멤버십 더블 혜택", EventStatus.PUBLISHED,
                "2026-09-01T00:00:00+09:00", "2026-11-30T23:59:59+09:00",
                "2026-08-30T11:40:00+09:00", null, MembershipGrade.NORMAL,
                html("<section data-block=\"hero\"><h1>가을 멤버십 더블 혜택</h1></section>")));
        save(reconstitute(5L, "새 요금제 사전예약", EventStatus.DRAFT,
                "2026-10-01T00:00:00+09:00", "2026-10-20T23:59:59+09:00",
                "2026-09-12T14:05:00+09:00", null, MembershipGrade.NORMAL, null));
        save(reconstitute(6L, "여름 데이터 대방출", EventStatus.ENDED,
                "2026-06-01T00:00:00+09:00", "2026-08-31T23:59:59+09:00",
                "2026-09-01T08:00:00+09:00", null, MembershipGrade.NORMAL,
                html("<section data-block=\"hero\"><h1>여름 데이터 대방출</h1></section>")));
        save(reconstitute(99L, "삭제된 이벤트", EventStatus.DRAFT,
                "2026-01-01T00:00:00+09:00", "2026-01-10T23:59:59+09:00",
                "2026-01-11T00:00:00+09:00", "2026-01-11T00:00:00+09:00",
                MembershipGrade.NORMAL, null));
    }

    private Event reconstitute(
            Long id,
            String title,
            EventStatus status,
            String start,
            String end,
            String updatedAt,
            String deletedAt,
            MembershipGrade grade,
            EventVersion publishedVersion) {
        return Event.reconstitute(
                id,
                systemAdmin,
                null,
                title,
                at(start),
                at(end),
                status,
                grade,
                deletedAt == null ? null : at(deletedAt),
                publishedVersion,
                at(updatedAt));
    }

    private static EventVersion html(String content) {
        return EventVersion.htmlOnly(content);
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }
}
