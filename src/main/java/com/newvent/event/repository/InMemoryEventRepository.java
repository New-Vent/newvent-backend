package com.newvent.event.repository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.MembershipGrade;

/**
 * JPA/Flyway 켜기 전 조회·생성 API 용 저장소.
 * 시드는 이수현 이벤트 더미(events.json)와 같은 id·상태·기간을 쓴다.
 */
@Repository
public class InMemoryEventRepository implements EventRepository {

    private final Map<Long, Event> events = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public InMemoryEventRepository() {
        seed();
        long maxId = events.keySet().stream().mapToLong(Long::longValue).max().orElse(0L);
        sequence.set(maxId);
    }

    @Override
    public List<Event> findAll() {
        return events.values().stream()
                .sorted(Comparator.comparing(Event::updatedAt).reversed()
                        .thenComparing(Event::id, Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public Optional<Event> findById(Long id) {
        return Optional.ofNullable(events.get(id));
    }

    @Override
    public Event save(Event event) {
        Long id = event.id();
        if (id == null) {
            id = sequence.incrementAndGet();
            event = new Event(
                    id,
                    event.name(),
                    event.status(),
                    event.startAt(),
                    event.endAt(),
                    event.updatedAt(),
                    event.deletedAt(),
                    event.template(),
                    event.thumbnailUrl(),
                    event.targetGrades(),
                    event.completedHtml());
        } else {
            sequence.accumulateAndGet(id, Math::max);
        }
        events.put(id, event);
        return event;
    }

    private void seed() {
        save(new Event(1L, "신규 가입 데이터 쿠폰 3GB", EventStatus.PUBLISHED,
                at("2026-09-16T00:00:00+09:00"), at("2026-10-15T23:59:59+09:00"),
                at("2026-09-16T10:20:00+09:00"), null, "signup", null,
                List.of(MembershipGrade.NORMAL, MembershipGrade.EXCELLENT, MembershipGrade.PREMIUM),
                "<section data-block=\"hero\"><h1>신규 가입 데이터 쿠폰 3GB</h1></section>"));
        save(new Event(2L, "월드컵 스코어 맞추기", EventStatus.DRAFT,
                at("2026-07-01T00:00:00+09:00"), at("2026-07-31T23:59:59+09:00"),
                at("2026-06-28T16:00:00+09:00"), null, "draw", null,
                List.of(MembershipGrade.EXCELLENT),
                null));
        save(new Event(3L, "지금 긁으면 바로 당첨", EventStatus.PUBLISHED,
                at("2026-09-16T00:00:00+09:00"), at("2026-09-18T23:59:59+09:00"),
                at("2026-09-15T09:10:00+09:00"), null, "instant", null,
                List.of(MembershipGrade.NORMAL),
                "<section data-block=\"hero\"><h1>지금 긁으면 바로 당첨</h1></section>"));
        save(new Event(4L, "가을 멤버십 더블 혜택", EventStatus.PUBLISHED,
                at("2026-09-01T00:00:00+09:00"), at("2026-11-30T23:59:59+09:00"),
                at("2026-08-30T11:40:00+09:00"), null, "season", null,
                List.of(MembershipGrade.NORMAL),
                "<section data-block=\"hero\"><h1>가을 멤버십 더블 혜택</h1></section>"));
        save(new Event(5L, "새 요금제 사전예약", EventStatus.DRAFT,
                at("2026-10-01T00:00:00+09:00"), at("2026-10-20T23:59:59+09:00"),
                at("2026-09-12T14:05:00+09:00"), null, "preorder", null,
                List.of(),
                null));
        save(new Event(6L, "여름 데이터 대방출", EventStatus.CLOSED,
                at("2026-06-01T00:00:00+09:00"), at("2026-08-31T23:59:59+09:00"),
                at("2026-09-01T08:00:00+09:00"), null, "signup", null,
                List.of(MembershipGrade.NORMAL, MembershipGrade.EXCELLENT, MembershipGrade.PREMIUM),
                "<section data-block=\"hero\"><h1>여름 데이터 대방출</h1></section>"));
        save(new Event(99L, "삭제된 이벤트", EventStatus.DRAFT,
                at("2026-01-01T00:00:00+09:00"), at("2026-01-10T23:59:59+09:00"),
                at("2026-01-11T00:00:00+09:00"), at("2026-01-11T00:00:00+09:00"),
                "signup", null, List.of(), null));
    }

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }
}
