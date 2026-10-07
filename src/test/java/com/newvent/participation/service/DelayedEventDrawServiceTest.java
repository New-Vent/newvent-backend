package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.domain.Game;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;

@ExtendWith(MockitoExtension.class)
class DelayedEventDrawServiceTest {

    private static final Long EVENT_ID = 1L;

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse("2026-10-15T14:00:00+09:00");

    private static final OffsetDateTime END_DATE = NOW.minusDays(1);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventGameConfigRepository eventGameConfigRepository;

    @Mock
    private EventParticipationRepository eventParticipationRepository;

    @Mock
    private Event event;

    @Mock
    private EventGameConfig gameConfig;

    @Mock
    private Game game;

    private DelayedEventDrawService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), NOW.getOffset());

        service = new DelayedEventDrawService(
            eventRepository,
            eventGameConfigRepository,
            eventParticipationRepository,
            clock
        );
    }

    @Test
    void 추첨_후보를_현재_시각과_게시_종료_상태로_조회한다() {
        when(eventParticipationRepository.findDelayedDrawCandidateEventIds(
            anyList(), any(OffsetDateTime.class)
        )).thenReturn(List.of(EVENT_ID));

        assertThat(service.findCandidateEventIds()).containsExactly(EVENT_ID);

        verify(eventParticipationRepository).findDelayedDrawCandidateEventIds(
            eq(List.of(EventStatus.PUBLISHED, EventStatus.ENDED)),
            eq(NOW)
        );
    }

    @Test
    void 발표_시각과_같으면_설정된_인원만큼_당첨시킨다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW, "커피 쿠폰");

        List<EventParticipation> participants = pendingParticipants(5);

        when(eventParticipationRepository.findPendingByEventId(EVENT_ID))
            .thenReturn(participants);

        service.drawEvent(EVENT_ID);

        assertResults(participants, 2, 3);
    }

    @Test
    void 종료_상태인_이벤트도_추첨한다() {
        prepareEvent(EventStatus.ENDED);
        prepareConfig(1, NOW.minusHours(1), "커피 쿠폰");

        List<EventParticipation> participants = pendingParticipants(3);

        when(eventParticipationRepository.findPendingByEventId(EVENT_ID))
            .thenReturn(participants);

        service.drawEvent(EVENT_ID);

        assertResults(participants, 1, 2);
    }

    @Test
    void 참여자가_당첨_인원보다_적으면_전원_당첨시킨다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(10, NOW, "커피 쿠폰");

        List<EventParticipation> participants = pendingParticipants(3);

        when(eventParticipationRepository.findPendingByEventId(EVENT_ID))
            .thenReturn(participants);

        service.drawEvent(EVENT_ID);

        assertResults(participants, 3, 0);
    }

    @Test
    void 발표_시각_전에는_추첨하지_않는다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW.plusMinutes(1), "커피 쿠폰");

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository, never())
            .findPendingByEventId(any());
    }

    @Test
    void 이벤트가_없으면_건너뛴다() {
        when(eventRepository.findByIdForDraw(EVENT_ID))
            .thenReturn(Optional.empty());

        service.drawEvent(EVENT_ID);

        verify(eventGameConfigRepository, never()).findAllByEventId(any());
    }

    @Test
    void 삭제된_이벤트는_건너뛴다() {
        when(eventRepository.findByIdForDraw(EVENT_ID))
            .thenReturn(Optional.of(event));
        when(event.getDeletedAt()).thenReturn(NOW.minusHours(1));

        service.drawEvent(EVENT_ID);

        verify(eventGameConfigRepository, never()).findAllByEventId(any());
    }

    @Test
    void 마감_시각과_같으면_아직_추첨하지_않는다() {
        when(eventRepository.findByIdForDraw(EVENT_ID))
            .thenReturn(Optional.of(event));
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getEndDate()).thenReturn(NOW);

        service.drawEvent(EVENT_ID);

        verify(eventGameConfigRepository, never()).findAllByEventId(any());
    }

    @Test
    void 설정이_여러_개이면_실패한다() {
        prepareEvent(EventStatus.PUBLISHED);

        when(eventGameConfigRepository.findAllByEventId(EVENT_ID))
            .thenReturn(List.of(gameConfig, gameConfig));

        assertThatThrownBy(() -> service.drawEvent(EVENT_ID))
            .isInstanceOfSatisfying(
                ParticipationException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED)
            );

        verify(eventParticipationRepository, never())
            .findPendingByEventId(any());
    }

    @Test
    void 스포츠_예측형은_추첨하지_않는다() {
        prepareEvent(EventStatus.PUBLISHED);

        when(eventGameConfigRepository.findAllByEventId(EVENT_ID))
            .thenReturn(List.of(gameConfig));
        when(gameConfig.getGame()).thenReturn(game);
        when(game.getCode()).thenReturn("SPORTS_PREDICTION");

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository, never())
            .findPendingByEventId(any());
    }

    @Test
    void 당첨_인원이_소수이면_실패한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(1.5, NOW, "커피 쿠폰");

        assertThatThrownBy(() -> service.drawEvent(EVENT_ID))
            .isInstanceOfSatisfying(
                ParticipationException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED)
            );

        verify(eventParticipationRepository, never())
            .findPendingByEventId(any());
    }

    @Test
    void 당첨_인원이_0이면_실패한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(0, NOW, "커피 쿠폰");

        assertThatThrownBy(() -> service.drawEvent(EVENT_ID))
            .isInstanceOfSatisfying(
                ParticipationException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED)
            );
    }

    @Test
    void 발표_시각이_종료_시각과_같으면_실패한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, END_DATE, "커피 쿠폰");

        assertThatThrownBy(() -> service.drawEvent(EVENT_ID))
            .isInstanceOfSatisfying(
                ParticipationException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED)
            );

        verify(eventParticipationRepository, never())
            .findPendingByEventId(any());
    }

    @Test
    void 경품명이_비어_있으면_실패한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW, " ");

        assertThatThrownBy(() -> service.drawEvent(EVENT_ID))
            .isInstanceOfSatisfying(
                ParticipationException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED)
            );
    }

    @Test
    void 대기_참여자가_없으면_건너뛴다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW, "커피 쿠폰");

        when(eventParticipationRepository.findPendingByEventId(EVENT_ID))
            .thenReturn(List.of());

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository).findPendingByEventId(EVENT_ID);
    }

    @Test
    void 재실행에서_대기_참여자가_없으면_기존_결과를_유지한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW, "커피 쿠폰");

        List<EventParticipation> participants = pendingParticipants(5);

        // 실제 DB에서는 첫 실행 후 PENDING이 없어진다.
        when(eventParticipationRepository.findPendingByEventId(EVENT_ID))
            .thenReturn(participants)
            .thenReturn(List.of());

        service.drawEvent(EVENT_ID);

        List<Map<String, Object>> firstResults = participants.stream()
            .map(p -> Map.copyOf(p.getResultData()))
            .toList();

        service.drawEvent(EVENT_ID);

        assertThat(participants.stream()
            .map(EventParticipation::getResultData)
            .toList())
            .containsExactlyElementsOf(firstResults);
    }

    private void prepareEvent(EventStatus status) {
        when(eventRepository.findByIdForDraw(EVENT_ID))
            .thenReturn(Optional.of(event));
        when(event.getStatus()).thenReturn(status);
        when(event.getEndDate()).thenReturn(END_DATE);
    }

    private void prepareConfig(
        Number winnerCount,
        OffsetDateTime announcementAt,
        String prizeName
    ) {
        when(eventGameConfigRepository.findAllByEventId(EVENT_ID))
            .thenReturn(List.of(gameConfig));
        when(gameConfig.getGame()).thenReturn(game);
        when(game.getCode()).thenReturn("BASIC");
        when(gameConfig.getConfig()).thenReturn(Map.of(
            "resultMode", "DELAYED",
            "winnerCount", winnerCount,
            "announcementAt", announcementAt.toString(),
            "prizeName", prizeName
        ));
    }

    private List<EventParticipation> pendingParticipants(int count) {
        List<EventParticipation> participants = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            participants.add(EventParticipation.create(
                event,
                null,
                Map.of(),
                Map.of("status", "PENDING")
            ));
        }

        return participants;
    }

    private void assertResults(
        List<EventParticipation> participants,
        int winnerCount,
        int loserCount
    ) {
        assertThat(participants.stream()
            .filter(p -> "WON".equals(p.getResultData().get("status")))
            .count())
            .isEqualTo(winnerCount);

        assertThat(participants.stream()
            .filter(p -> "LOST".equals(p.getResultData().get("status")))
            .count())
            .isEqualTo(loserCount);

        for (EventParticipation participation : participants) {
            Map<String, Object> result = participation.getResultData();

            if ("WON".equals(result.get("status"))) {
                assertThat(result).isEqualTo(Map.of(
                    "status", "WON",
                    "prizeName", "커피 쿠폰"
                ));
            } else {
                assertThat(result).isEqualTo(Map.of("status", "LOST"));
            }
        }
    }
}
