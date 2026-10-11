package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.exception.EventException;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.service.PublicEventService;
import com.newvent.participation.domain.EventGameConfig;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.domain.Game;
import com.newvent.participation.dto.request.ParticipationCreateRequest;
import com.newvent.participation.dto.response.ParticipationCreateResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventGameConfigRepository;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.service.UserService;

class ParticipationServiceTest  {

    private final PublicEventService publicEventService = mock(PublicEventService.class);
    private final UserService userService = mock(UserService.class);
    private final EventParticipationRepository participationRepository = mock(EventParticipationRepository.class);
    private final EventGameConfigRepository eventGameConfigRepository = mock(EventGameConfigRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);

    private final ParticipationService service = new ParticipationService(
            publicEventService,
            userService,
            participationRepository,
            eventGameConfigRepository,
            new ParticipationValidator(),
            new ParticipationResultProcessor(),
            eventRepository
    );

    @BeforeEach
    void setUpBasicConfig() {
        when(eventRepository.findByIdForDraw(1L))
            .thenReturn(Optional.of(event(EventStatus.PUBLISHED, MembershipGrade.NORMAL)));

        Game game = mock(Game.class);
        when(game.getCode()).thenReturn("BASIC");
        when(game.isActive()).thenReturn(true);

        EventGameConfig config = mock(EventGameConfig.class);
        when(config.getGame()).thenReturn(game);
        when(config.getConfig()).thenReturn(Map.of());

        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(List.of(config));
    }

    @Test
    void 참여하면_사용자와_이벤트를연결하고_빈JSON데이터로저장한다() {
        Event event = event(EventStatus.PUBLISHED, MembershipGrade.EXCELLENT);
        User user = user(MembershipGrade.BEST);

        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(userService.getById(7L)).thenReturn(user);
        when(participationRepository.saveAndFlush(any(EventParticipation.class)))
                .thenAnswer(invocation -> {
                    EventParticipation participation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(participation, "id", 30L);
                    return participation;
                });

        ParticipationCreateResponse result = service.participate(1L, 7L, null);

        assertEquals(30L, result.participationId());
        assertEquals(1L, result.eventId());

        ArgumentCaptor<EventParticipation> captor =
                ArgumentCaptor.forClass(EventParticipation.class);
        verify(participationRepository).saveAndFlush(captor.capture());

        EventParticipation saved = captor.getValue();
        assertSame(event, saved.getEvent());
        assertSame(user, saved.getUser());
        assertTrue(saved.getSubmittedData().isEmpty());
        assertTrue(saved.getResultData().isEmpty());
    }

    @Test
    void 등급이부족하면_저장하지않는다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.PUBLISHED, MembershipGrade.BEST));
        when(userService.getById(7L)).thenReturn(user(MembershipGrade.NORMAL));

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.participate(1L, 7L, null));

        assertEquals(ParticipationErrorCode.INSUFFICIENT_GRADE, exception.getErrorCode());
        verify(participationRepository, never()).saveAndFlush(any(EventParticipation.class));
    }

    @Test
    void 이미참여했다면_저장하지않는다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.PUBLISHED, MembershipGrade.NORMAL));
        when(userService.getById(7L)).thenReturn(user(MembershipGrade.NORMAL));
        when(participationRepository.existsByEventIdAndUserId(1L, 7L))
                .thenReturn(true);

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.participate(1L, 7L, null));

        assertEquals(ParticipationErrorCode.ALREADY_PARTICIPATED, exception.getErrorCode());
        verify(participationRepository, never()).saveAndFlush(any(EventParticipation.class));
    }

    @Test
    void 참여유일제약에걸리면_중복참여로처리한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.PUBLISHED, MembershipGrade.NORMAL));
        when(userService.getById(7L)).thenReturn(user(MembershipGrade.NORMAL));

        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName())
                .thenReturn("uk_event_participations_event_user");

        when(participationRepository.saveAndFlush(any(EventParticipation.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "참여 유일 제약 위반", constraintViolation));

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.participate(1L, 7L, null));

        assertEquals(ParticipationErrorCode.ALREADY_PARTICIPATED, exception.getErrorCode());
    }

    @Test
    void 다른DB제약위반은_중복참여로바꾸지않는다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.PUBLISHED, MembershipGrade.NORMAL));
        when(userService.getById(7L)).thenReturn(user(MembershipGrade.NORMAL));

        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName())
                .thenReturn("some_other_constraint");

        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException(
                        "다른 DB 제약 위반", constraintViolation);

        when(participationRepository.saveAndFlush(any(EventParticipation.class)))
                .thenThrow(databaseException);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.participate(1L, 7L, null));

        assertSame(databaseException, thrown);
    }

    @Test
    void 접근할수없는이벤트는_참여할수없다() {
        when(publicEventService.getPublicEvent(1L))
                .thenThrow(new EventNotAccessibleException());

        assertThrows(
                EventNotAccessibleException.class,
                () -> service.participate(1L, 7L, null));

        verify(participationRepository, never()).saveAndFlush(any(EventParticipation.class));
    }

    @Test
    void 게시상태가아니면_참여할수없다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.ENDED, MembershipGrade.NORMAL));

        assertThrows(EventException.class, () -> service.participate(1L, 7L, null));
        verify(participationRepository, never()).saveAndFlush(any(EventParticipation.class));
    }

    @Test
    void 이미참여했고_등급도부족하면_중복참여를우선한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.PUBLISHED, MembershipGrade.BEST));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));
        when(participationRepository.existsByEventIdAndUserId(1L, 7L))
                .thenReturn(true);

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.participate(1L, 7L, null));

        assertEquals(
                ParticipationErrorCode.ALREADY_PARTICIPATED,
                exception.getErrorCode());
        verify(participationRepository, never())
                .saveAndFlush(any(EventParticipation.class));
    }

    @Test
    void 예측형_참여는_제출한_예측값을_참여기록에_저장한다() {
        Event event = event(EventStatus.PUBLISHED, MembershipGrade.NORMAL);
        User user = user(MembershipGrade.NORMAL);

        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(userService.getById(7L)).thenReturn(user);

        Game game = mock(Game.class);
        when(game.getCode()).thenReturn("SPORTS_PREDICTION");
        when(game.isActive()).thenReturn(true);

        EventGameConfig config = mock(EventGameConfig.class);
        when(config.getGame()).thenReturn(game);
        when(config.getConfig()).thenReturn(
                Map.of(
                        "predictionOptions",
                        List.of("HOME_WIN", "DRAW", "AWAY_WIN")));

        // setUpBasicConfig()의 기본형 설정을 이 테스트에서는 예측형으로 덮어쓴다.
        when(eventGameConfigRepository.findAllByEventId(1L))
                .thenReturn(List.of(config));

        when(participationRepository.saveAndFlush(any(EventParticipation.class)))
                .thenAnswer(invocation -> {
                    EventParticipation participation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(participation, "id", 30L);
                    return participation;
                });

        ParticipationCreateResponse response = service.participate(
                1L,
                7L,
                new ParticipationCreateRequest("HOME_WIN", null, null));

        ArgumentCaptor<EventParticipation> captor =
                ArgumentCaptor.forClass(EventParticipation.class);

        verify(participationRepository).saveAndFlush(captor.capture());

        EventParticipation saved = captor.getValue();

        assertSame(event, saved.getEvent());
        assertSame(user, saved.getUser());
        assertEquals(
                Map.of("prediction", "HOME_WIN"),
                saved.getSubmittedData());

        assertEquals(
                Map.of("status", "PENDING"),
                saved.getResultData()
        );
        assertEquals(saved.getResultData(), response.resultData());

        assertEquals(30L, response.participationId());
        assertEquals(1L, response.eventId());
    }

    @Test
    void 즉시추첨_결과를_참여기록에_저장하고_같은결과를_응답한다() {
        Event event = event(EventStatus.PUBLISHED, MembershipGrade.NORMAL);
        User user = user(MembershipGrade.NORMAL);

        when(publicEventService.getPublicEvent(1L)).thenReturn(event);
        when(userService.getById(7L)).thenReturn(user);

        Game game = mock(Game.class);
        when(game.getCode()).thenReturn("LUCKY_POUCH");
        when(game.isActive()).thenReturn(true);

        EventGameConfig config = mock(EventGameConfig.class);
        when(config.getGame()).thenReturn(game);
        when(config.getConfig()).thenReturn(
            Map.of("pouches", List.of(
                Map.of(
                    "pouchIndex", 1,
                    "winProbability", 0,
                    "prizeName", "커피 쿠폰"),
                Map.of(
                    "pouchIndex", 2,
                    "winProbability", 100,
                    "prizeName", "치킨 쿠폰"),
                Map.of(
                    "pouchIndex", 3,
                    "winProbability", 50,
                    "prizeName", "편의점 상품권"))));

        when(eventGameConfigRepository.findAllByEventId(1L)).thenReturn(List.of(config));

        when(participationRepository.saveAndFlush(any(EventParticipation.class)))
            .thenAnswer(invocation -> {
                EventParticipation participation = invocation.getArgument(0);
                ReflectionTestUtils.setField(participation, "id", 30L);
                return participation;
            });

        // 2번 주머니를 선택한다.
        ParticipationCreateResponse response = service.participate(
            1L,
            7L,
            new ParticipationCreateRequest(null, null, 2));

        ArgumentCaptor<EventParticipation> captor =
            ArgumentCaptor.forClass(EventParticipation.class);

        verify(participationRepository).saveAndFlush(captor.capture());

        EventParticipation saved = captor.getValue();

        // 선택 번호가 저장됐는지 확인한다.
        assertEquals(Map.of("pouchIndex", 2), saved.getSubmittedData());

        // 2번은 확률 100%이므로 치킨 쿠폰에 당첨되어야 한다.
        assertEquals(
            Map.of("status", "WON", "prizeName", "치킨 쿠폰"),
            saved.getResultData());

        // 저장한 결과와 응답 결과가 같은지 확인한다.
        assertEquals(saved.getResultData(), response.resultData());
        assertEquals(30L, response.participationId());
        assertEquals(1L, response.eventId());
    }

    @Test
    void 참여설정이없으면_저장하지않는다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.PUBLISHED, MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));
        when(eventGameConfigRepository.findAllByEventId(1L))
                .thenReturn(List.of());

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.participate(1L, 7L, null));

        assertEquals(
                ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                exception.getErrorCode());
        verify(participationRepository, never())
                .saveAndFlush(any(EventParticipation.class));
    }

    @Test
    void 참여설정이여러개면_저장하지않는다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(EventStatus.PUBLISHED, MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));

        EventGameConfig config1 = mock(EventGameConfig.class);
        EventGameConfig config2 = mock(EventGameConfig.class);

        when(eventGameConfigRepository.findAllByEventId(1L))
                .thenReturn(List.of(config1, config2));

        ParticipationException exception = assertThrows(
                ParticipationException.class,
                () -> service.participate(1L, 7L, null));

        assertEquals(
                ParticipationErrorCode.PARTICIPATION_NOT_CONFIGURED,
                exception.getErrorCode());
        verify(participationRepository, never())
                .saveAndFlush(any(EventParticipation.class));
    }

    private Event event(EventStatus status, MembershipGrade grade) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "status", status);
        ReflectionTestUtils.setField(event, "grade", grade);
        return event;
    }

    private User user(MembershipGrade grade) {
        return new User(
                "user07", "hash", "사용자", "user07@test.com",
                null, 50000, grade);
    }
}
