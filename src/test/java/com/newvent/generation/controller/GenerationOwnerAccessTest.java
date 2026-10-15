package com.newvent.generation.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.generation.exception.GenerationErrorCode;
import com.newvent.generation.exception.GenerationException;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.generation.service.GenerationService;

class GenerationOwnerAccessTest {

    static final String JOB_ID = "00000000-0000-0000-0000-000000000001";

    final GenerationService generation = mock(GenerationService.class);
    final GenerationJobStore jobs = mock(GenerationJobStore.class);
    final EventOwnerCheck ownerCheck = mock(EventOwnerCheck.class);
    final GenerationJobController jobController = new GenerationJobController(generation, jobs, ownerCheck);
    final GenerationPreviewController previewController = new GenerationPreviewController(generation, ownerCheck);

    void notOwner(AuthUser admin) {
        given(ownerCheck.requireOwned(2L, admin)).willThrow(new AccessDeniedException("denied"));
    }

    @Test
    @DisplayName("진행 상황 — 다른 관리자는 작업을 찾기 전에 403")
    void statusRejectsOtherAdmin() {
        AuthUser other = AuthUser.admin(9L);
        notOwner(other);

        assertThatThrownBy(() -> jobController.status(2L, JOB_ID, other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jobs);
    }

    @Test
    @DisplayName("생성 중단 — 다른 관리자는 403, 작업을 건드리지 않는다")
    void cancelRejectsOtherAdmin() {
        AuthUser other = AuthUser.admin(9L);
        notOwner(other);

        assertThatThrownBy(() -> jobController.cancel(2L, JOB_ID, other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jobs, generation);
    }

    @Test
    @DisplayName("진행 상황 — 소유 관리자는 작업을 찾는다 (없으면 GEN404-0)")
    void statusLooksUpJobForOwner() {
        AuthUser owner = AuthUser.admin(1L);
        given(jobs.byId(JOB_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> jobController.status(2L, JOB_ID, owner))
                .isInstanceOf(GenerationException.class)
                .extracting(e -> ((GenerationException) e).getErrorCode())
                .isEqualTo(GenerationErrorCode.JOB_NOT_FOUND);
        verify(ownerCheck).requireOwned(2L, owner);
    }

    @Test
    @DisplayName("미리보기 — 다른 관리자는 403, 렌더링하지 않는다")
    void previewRejectsOtherAdmin() {
        AuthUser other = AuthUser.admin(9L);
        notOwner(other);

        assertThatThrownBy(() -> previewController.preview(2L, other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(generation);
    }

    @Test
    @DisplayName("미리보기 — 소유 관리자는 렌더링한다 (페이지가 없으면 GEN404-1)")
    void previewRendersForOwner() {
        AuthUser owner = AuthUser.admin(1L);
        Event event = mock(Event.class);
        given(event.getId()).willReturn(2L);
        given(ownerCheck.requireOwned(2L, owner)).willReturn(event);
        given(generation.render(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> previewController.preview(2L, owner))
                .isInstanceOf(GenerationException.class)
                .extracting(e -> ((GenerationException) e).getErrorCode())
                .isEqualTo(GenerationErrorCode.PAGE_NOT_FOUND);
        verify(generation).render(any());
    }
}
