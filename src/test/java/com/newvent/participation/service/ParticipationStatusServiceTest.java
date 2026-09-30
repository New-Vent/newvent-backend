package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventNotAccessibleException;
import com.newvent.event.service.PublicEventService;
import com.newvent.participation.domain.ParticipationUnavailableReason;
import com.newvent.participation.dto.response.ParticipationStatusResponse;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.service.UserService;

class ParticipationStatusServiceTest {

    private final PublicEventService publicEventService = mock(PublicEventService.class);
    private final UserService userService = mock(UserService.class);
    private final EventParticipationRepository participationRepository =
            mock(EventParticipationRepository.class);

    private final ParticipationStatusService service = new ParticipationStatusService(
            publicEventService, userService, participationRepository);

    @Test
    void 참여이력이없고_등급이충분하면_참여가능하다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.EXCELLENT));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.participated());
        assertTrue(result.canParticipate());
        assertNull(result.unavailableReason());
    }

    @Test
    void 이미참여했다면_참여불가사유를반환한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.NORMAL));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.BEST));
        when(participationRepository.existsByEventIdAndUserId(1L, 7L))
                .thenReturn(true);

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertTrue(result.participated());
        assertFalse(result.canParticipate());
        assertEquals(
                ParticipationUnavailableReason.ALREADY_PARTICIPATED,
                result.unavailableReason()
        );
    }

    @Test
    void 등급이부족하면_참여불가사유를반환한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.EXCELLENT));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertFalse(result.participated());
        assertFalse(result.canParticipate());
        assertEquals(
                ParticipationUnavailableReason.INSUFFICIENT_GRADE,
                result.unavailableReason()
        );
    }

    @Test
    void 이미참여했고_등급도부족하면_이미참여한사유를우선한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenReturn(event(MembershipGrade.BEST));
        when(userService.getById(7L))
                .thenReturn(user(MembershipGrade.NORMAL));
        when(participationRepository.existsByEventIdAndUserId(1L, 7L))
                .thenReturn(true);

        ParticipationStatusResponse result = service.getStatus(1L, 7L);

        assertTrue(result.participated());
        assertFalse(result.canParticipate());
        assertEquals(
                ParticipationUnavailableReason.ALREADY_PARTICIPATED,
                result.unavailableReason()
        );
    }

    @Test
    void 접근할수없는_이벤트는_기존예외를그대로반환한다() {
        when(publicEventService.getPublicEvent(1L))
                .thenThrow(new EventNotAccessibleException());

        assertThrows(
                EventNotAccessibleException.class,
                () -> service.getStatus(1L, 7L)
        );
    }

    private Event event(MembershipGrade grade) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "grade", grade);
        return event;
    }

    private User user(MembershipGrade grade) {
        return new User(
                "user07", "hash", "사용자", "user07@test.com",
                null, 50000, grade
        );
    }
}
