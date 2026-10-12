package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;

class EventOwnerCheckTest {

    final EventRepository events = mock(EventRepository.class);
    final EventOwnerCheck ownerCheck = new EventOwnerCheck(events);

    Event eventOwnedBy(Long adminId) {
        Event event = mock(Event.class);
        given(event.ownedBy(adminId)).willReturn(true);
        given(events.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.of(event));
        return event;
    }

    @Test
    @DisplayName("소유 관리자면 이벤트를 돌려준다")
    void owner() {
        Event event = eventOwnedBy(1L);

        assertThat(ownerCheck.requireOwned(2L, AuthUser.admin(1L))).isSameAs(event);
    }

    @Test
    @DisplayName("다른 관리자의 이벤트면 403")
    void otherAdmin() {
        eventOwnedBy(1L);

        assertThatThrownBy(() -> ownerCheck.requireOwned(2L, AuthUser.admin(9L)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("관리자가 아니거나 인증 정보가 없으면 403")
    void notAdmin() {
        eventOwnedBy(1L);

        assertThatThrownBy(() -> ownerCheck.requireOwned(2L, AuthUser.user(1L)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ownerCheck.requireOwned(2L, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("없거나 휴지통에 있는 이벤트면 404 (EVENT404-0)")
    void missing() {
        given(events.findByIdAndDeletedAtIsNull(2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ownerCheck.requireOwned(2L, AuthUser.admin(1L)))
                .isInstanceOf(EventException.class)
                .extracting(e -> ((EventException) e).getErrorCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
    }
}
