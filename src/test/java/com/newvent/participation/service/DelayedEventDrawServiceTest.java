package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.repository.EventRepository;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.Game;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
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

        List<Long> winnerIds = List.of(11L, 12L);

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 2))
            .thenReturn(winnerIds);
        when(eventParticipationRepository.updateWinners(
            EVENT_ID, winnerIds, "커피 쿠폰"
        )).thenReturn(2);
        when(eventParticipationRepository.updatePendingAsLostInBatch(EVENT_ID, 1_000))
            .thenReturn(3, 0);

        service.drawEvent(EVENT_ID);

        var order = inOrder(eventParticipationRepository);
        order.verify(eventParticipationRepository)
            .findRandomPendingIds(EVENT_ID, 2);
        order.verify(eventParticipationRepository)
            .updateWinners(EVENT_ID, winnerIds, "커피 쿠폰");
        order.verify(eventParticipationRepository, times(2))
            .updatePendingAsLostInBatch(EVENT_ID, 1_000);
    }

    @Test
    void 종료_상태인_이벤트도_추첨한다() {
        prepareEvent(EventStatus.ENDED);
        prepareConfig(1, NOW.minusHours(1), "커피 쿠폰");

        List<Long> winnerIds = List.of(11L);

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 1))
            .thenReturn(winnerIds);
        when(eventParticipationRepository.updateWinners(
            EVENT_ID, winnerIds, "커피 쿠폰"
        )).thenReturn(1);
        when(eventParticipationRepository.updatePendingAsLostInBatch(EVENT_ID, 1_000))
            .thenReturn(2, 0);

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository)
            .updateWinners(EVENT_ID, winnerIds, "커피 쿠폰");
        verify(eventParticipationRepository, times(2))
            .updatePendingAsLostInBatch(EVENT_ID, 1_000);
    }

    @Test
    void 참여자가_당첨_인원보다_적으면_전원_당첨시킨다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(10, NOW, "커피 쿠폰");

        List<Long> winnerIds = List.of(11L, 12L, 13L);

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 10))
            .thenReturn(winnerIds);

        // 서비스 호출 전에 등록한다.
        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 7))
            .thenReturn(List.of());

        when(eventParticipationRepository.updateWinners(
            EVENT_ID, winnerIds, "커피 쿠폰"
        )).thenReturn(3);

        when(eventParticipationRepository.updatePendingAsLostInBatch(EVENT_ID, 1_000))
            .thenReturn(0);

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository)
            .updateWinners(EVENT_ID, winnerIds, "커피 쿠폰");
        verify(eventParticipationRepository)
            .findRandomPendingIds(EVENT_ID, 7);
        verify(eventParticipationRepository)
            .updatePendingAsLostInBatch(EVENT_ID, 1_000);
    }

    @Test
    void 대기_참여자가_없으면_건너뛴다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW, "커피 쿠폰");

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 2))
            .thenReturn(List.of());

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository, never())
            .updateWinners(any(), anyList(), any());
        verify(eventParticipationRepository)
            .updatePendingAsLostInBatch(EVENT_ID, 1_000);
    }

    @Test
    void 재실행에서_대기_참여자가_없으면_결과를_다시_변경하지_않는다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW, "커피 쿠폰");

        List<Long> winnerIds = List.of(11L, 12L);

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 2))
            .thenReturn(winnerIds)
            .thenReturn(List.of());
        when(eventParticipationRepository.updateWinners(
            EVENT_ID, winnerIds, "커피 쿠폰"
        )).thenReturn(2);
        when(eventParticipationRepository.updatePendingAsLostInBatch(EVENT_ID, 1_000))
            .thenReturn(3, 0);

        service.drawEvent(EVENT_ID);
        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository, times(2))
            .findRandomPendingIds(EVENT_ID, 2);
        verify(eventParticipationRepository, times(1))
            .updateWinners(EVENT_ID, winnerIds, "커피 쿠폰");

        // 첫 실행: 3 → 0, 재실행: 0이므로 총 세 번 호출한다.
        verify(eventParticipationRepository, times(3))
            .updatePendingAsLostInBatch(EVENT_ID, 1_000);
    }

    @Test
    void 발표_시각_전에는_추첨하지_않는다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW.plusMinutes(1), "커피 쿠폰");

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository, never())
            .findRandomPendingIds(any(), anyInt());
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
            .findRandomPendingIds(any(), anyInt());
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
            .findRandomPendingIds(any(), anyInt());
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
            .findRandomPendingIds(any(), anyInt());
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
            .findRandomPendingIds(any(), anyInt());
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

    @Test
    void 당첨_인원_1000명은_허용한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(1_000, NOW, "커피 쿠폰");

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 1_000))
            .thenReturn(List.of());

        service.drawEvent(EVENT_ID);

        verify(eventParticipationRepository)
            .findRandomPendingIds(EVENT_ID, 1_000);
    }

    @Test
    void 당첨_인원이_2500명이면_1000명씩_나눠서_처리한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2_500, NOW, "커피 쿠폰");

        List<Long> first = LongStream.rangeClosed(1, 1_000).boxed().toList();
        List<Long> second = LongStream.rangeClosed(1_001, 2_000).boxed().toList();
        List<Long> third = LongStream.rangeClosed(2_001, 2_500).boxed().toList();

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 1_000))
            .thenReturn(first, second);
        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 500))
            .thenReturn(third);

        when(eventParticipationRepository.updateWinners(EVENT_ID, first, "커피 쿠폰"))
            .thenReturn(1_000);
        when(eventParticipationRepository.updateWinners(EVENT_ID, second, "커피 쿠폰"))
            .thenReturn(1_000);
        when(eventParticipationRepository.updateWinners(EVENT_ID, third, "커피 쿠폰"))
            .thenReturn(500);

        when(eventParticipationRepository.updatePendingAsLostInBatch(EVENT_ID, 1_000))
            .thenReturn(1_000, 500, 0);

        service.drawEvent(EVENT_ID);

        var order = inOrder(eventParticipationRepository);

        order.verify(eventParticipationRepository)
            .findRandomPendingIds(EVENT_ID, 1_000);
        order.verify(eventParticipationRepository)
            .updateWinners(EVENT_ID, first, "커피 쿠폰");

        order.verify(eventParticipationRepository)
            .findRandomPendingIds(EVENT_ID, 1_000);
        order.verify(eventParticipationRepository)
            .updateWinners(EVENT_ID, second, "커피 쿠폰");

        order.verify(eventParticipationRepository)
            .findRandomPendingIds(EVENT_ID, 500);
        order.verify(eventParticipationRepository)
            .updateWinners(EVENT_ID, third, "커피 쿠폰");

        order.verify(eventParticipationRepository, times(3))
            .updatePendingAsLostInBatch(EVENT_ID, 1_000);

        order.verifyNoMoreInteractions();
    }

    @Test
    void 당첨_변경_건수가_선정_인원과_다르면_실패한다() {
        prepareEvent(EventStatus.PUBLISHED);
        prepareConfig(2, NOW, "커피 쿠폰");

        List<Long> winnerIds = List.of(11L, 12L);

        when(eventParticipationRepository.findRandomPendingIds(EVENT_ID, 2))
            .thenReturn(winnerIds);
        when(eventParticipationRepository.updateWinners(
            EVENT_ID, winnerIds, "커피 쿠폰"
        )).thenReturn(1);

        assertThatThrownBy(() -> service.drawEvent(EVENT_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("변경 건수가 일치하지 않습니다");

        verify(eventParticipationRepository, never())
            .updatePendingAsLostInBatch(any(), anyInt());
    }
}
