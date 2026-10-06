package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.newvent.event.domain.Event;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.dto.request.ParticipationListFilter;
import com.newvent.participation.dto.response.MyParticipationListResponse;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.participation.repository.ParticipationSummaryProjection;

class MyParticipationServiceTest {

    private final EventParticipationRepository repository =
            mock(EventParticipationRepository.class);

    private final MyParticipationService service =
            new MyParticipationService(repository);

    @Test
    void 전체_참여_목록과_요약을_반환한다() {
        PageRequest pageable = PageRequest.of(0, 10);
        EventParticipation participation = participation(
                30L,
                10L,
                "가을 이벤트",
                Map.of("status", "WON", "prizeName", "커피 쿠폰")
        );

        when(repository.findMyParticipations(7L, pageable))
                .thenReturn(new PageImpl<>(
                        List.of(participation), pageable, 1
                ));
        stubSummary(7L, 1, 1, 0);

        MyParticipationListResponse response =
                service.getParticipations(
                        7L, ParticipationListFilter.ALL, 0, 10
                );

        assertThat(response.summary().totalParticipationCount())
                .isEqualTo(1);
        assertThat(response.summary().rewardCount()).isEqualTo(1);
        assertThat(response.summary().pendingCount()).isZero();

        assertThat(response.participations().content())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.participationId()).isEqualTo(30L);
                    assertThat(item.eventId()).isEqualTo(10L);
                    assertThat(item.eventTitle()).isEqualTo("가을 이벤트");
                    assertThat(item.participatedAt()).isEqualTo(
                            OffsetDateTime.parse(
                                    "2026-10-01T15:00:00+09:00"
                            )
                    );
                    assertThat(item.resultStatus()).isEqualTo("WON");
                    assertThat(item.prizeName()).isEqualTo("커피 쿠폰");
                });

        verify(repository, never())
                .findMyParticipationsByResultStatus(
                        anyLong(), anyString(), any()
                );
    }

    @Test
    void 당첨_목록의_다음_페이지에서도_요약은_전체_기록_기준이다() {
        PageRequest pageable = PageRequest.of(1, 2);
        EventParticipation participation = participation(
                30L,
                10L,
                "당첨 이벤트",
                Map.of("status", "WON", "prizeName", "커피 쿠폰")
        );

        when(repository.findMyParticipationsByResultStatus(
                7L, "WON", pageable
        )).thenReturn(new PageImpl<>(
                List.of(participation), pageable, 3
        ));
        stubSummary(7L, 12, 3, 4);

        MyParticipationListResponse response =
                service.getParticipations(
                        7L, ParticipationListFilter.REWARDS, 1, 2
                );

        assertThat(response.summary().totalParticipationCount())
                .isEqualTo(12);
        assertThat(response.summary().rewardCount()).isEqualTo(3);
        assertThat(response.summary().pendingCount()).isEqualTo(4);

        assertThat(response.participations().page()).isEqualTo(1);
        assertThat(response.participations().size()).isEqualTo(2);
        assertThat(response.participations().totalElements())
                .isEqualTo(3);
        assertThat(response.participations().totalPages())
                .isEqualTo(2);

        verify(repository, never())
                .findMyParticipations(anyLong(), any());
        verify(repository).findSummaryByUserId(7L);
    }

    @Test
    void 참여_기록이_없으면_빈_목록과_0인_요약을_반환한다() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(repository.findMyParticipations(7L, pageable))
                .thenReturn(Page.empty(pageable));
        stubSummary(7L, 0, 0, 0);

        MyParticipationListResponse response =
                service.getParticipations(
                        7L, ParticipationListFilter.ALL, 0, 10
                );

        assertThat(response.participations().content()).isEmpty();
        assertThat(response.participations().totalElements()).isZero();
        assertThat(response.participations().totalPages()).isZero();
        assertThat(response.summary().totalParticipationCount()).isZero();
        assertThat(response.summary().rewardCount()).isZero();
        assertThat(response.summary().pendingCount()).isZero();
    }

    @Test
    void 페이지가_비어도_전체_요약은_유지된다() {
        PageRequest pageable = PageRequest.of(5, 10);

        when(repository.findMyParticipations(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 12));
        stubSummary(7L, 12, 3, 4);

        MyParticipationListResponse response =
                service.getParticipations(
                        7L, ParticipationListFilter.ALL, 5, 10
                );

        assertThat(response.participations().content()).isEmpty();
        assertThat(response.participations().totalElements())
                .isEqualTo(12);
        assertThat(response.summary().totalParticipationCount())
                .isEqualTo(12);
        assertThat(response.summary().rewardCount()).isEqualTo(3);
        assertThat(response.summary().pendingCount()).isEqualTo(4);
    }

    @Test
    void 빈_결과의_상태와_경품명은_null로_반환한다() {
        PageRequest pageable = PageRequest.of(0, 10);
        EventParticipation participation = participation(
                30L, 10L, "단순 참여 이벤트", Map.of()
        );

        when(repository.findMyParticipations(7L, pageable))
                .thenReturn(new PageImpl<>(
                        List.of(participation), pageable, 1
                ));
        stubSummary(7L, 1, 0, 0);

        MyParticipationListResponse response =
                service.getParticipations(
                        7L, ParticipationListFilter.ALL, 0, 10
                );

        assertThat(response.participations().content())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.resultStatus()).isNull();
                    assertThat(item.prizeName()).isNull();
                });
    }

    private void stubSummary(
            Long userId,
            long total,
            long rewards,
            long pending
    ) {
        ParticipationSummaryProjection counts = mock(ParticipationSummaryProjection.class);

        when(counts.getTotalParticipationCount()).thenReturn(total);
        when(counts.getRewardCount()).thenReturn(rewards);
        when(counts.getPendingCount()).thenReturn(pending);
        when(repository.findSummaryByUserId(userId)).thenReturn(counts);
    }

    private EventParticipation participation(
            Long id,
            Long eventId,
            String title,
            Map<String, Object> resultData
    ) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getTitle()).thenReturn(title);

        EventParticipation participation = mock(EventParticipation.class);
        when(participation.getId()).thenReturn(id);
        when(participation.getEvent()).thenReturn(event);
        when(participation.getCreatedAt()).thenReturn(
                OffsetDateTime.parse("2026-10-01T15:00:00+09:00")
        );
        when(participation.getResultData()).thenReturn(resultData);

        return participation;
    }
}
