package com.newvent.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.auth.dto.AuthUser;
import com.newvent.event.domain.Event;
import com.newvent.event.service.EventOwnerCheck;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.domain.ParticipationResultStatus;
import com.newvent.participation.dto.response.EventParticipantListResponse;
import com.newvent.participation.dto.response.EventParticipantResponse;
import com.newvent.participation.repository.EventParticipationRepository;
import com.newvent.participation.repository.ParticipationSummaryProjection;
import com.newvent.user.domain.User;

class EventParticipantServiceTest {

    private static final AuthUser ADMIN = AuthUser.admin(3L);

    private final EventOwnerCheck ownerCheck = mock(EventOwnerCheck.class);
    private final EventParticipationRepository participationRepository =
            mock(EventParticipationRepository.class);

    private final EventParticipantService service =
            new EventParticipantService(ownerCheck, participationRepository);

    @Test
    @DisplayName("참여 기록을 사람 중심으로 바꿔 준다 — 이름 · 입력 · 결과")
    void 참여자목록을_준다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        givenSummary(20, 7, 0);
        givenPage(List.of(
                participation(6699L, user(3L, "권도하"), Map.of(), Map.of(
                        "status", "WON", "prizeName", "데이터 5GB 쿠폰")),
                participation(6698L, user(9L, "김철수"), Map.of("phoneNumber", "01012345678"),
                        Map.of("status", "LOST"))));

        EventParticipantListResponse result =
                service.getParticipants(1L, ADMIN, null, 0, 20);

        assertEquals(2, result.participants().content().size());

        EventParticipantResponse won = result.participants().content().getFirst();
        assertEquals(6699L, won.participationId());
        assertEquals("권도하", won.userName());
        assertEquals("WON", won.resultStatus());
        assertEquals("데이터 5GB 쿠폰", won.prizeName());

        EventParticipantResponse lost = result.participants().content().get(1);
        assertEquals("LOST", lost.resultStatus());
        assertNull(lost.prizeName(), "당첨이 아니면 경품명이 없다");
        assertEquals("01012345678", lost.submittedData().get("phoneNumber"),
                "참여 입력은 저장된 그대로 — 경품을 보내려면 관리자가 봐야 한다");
    }

    @Test
    @DisplayName("요약은 필터와 무관하게 전체를 센다 — 당첨만 걸러도 전체 참여 수가 보인다")
    void 요약은_필터와무관하다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        givenSummary(20, 7, 2);
        givenPage(List.of(participation(1L, user(3L, "권도하"), Map.of(), Map.of("status", "WON"))));

        EventParticipantListResponse result =
                service.getParticipants(1L, ADMIN, ParticipationResultStatus.WON, 0, 20);

        assertEquals(20, result.summary().totalParticipationCount());
        assertEquals(7, result.summary().rewardCount());
        assertEquals(2, result.summary().pendingCount());
        assertEquals(1, result.participants().content().size(), "목록만 걸러진다");
    }

    @Test
    @DisplayName("resultStatus 를 그대로 넘긴다 — 당첨자 명단은 WON 이다")
    void 필터를_레포지토리까지_넘긴다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        givenSummary(0, 0, 0);
        givenPage(List.of());

        service.getParticipants(1L, ADMIN, ParticipationResultStatus.WON, 0, 20);

        verify(participationRepository).findByEventId(eq(1L), eq("WON"), any(Pageable.class));
    }

    @Test
    @DisplayName("필터가 없으면 null 로 넘긴다 — 쿼리가 조건을 건너뛴다")
    void 필터없으면_null이다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        givenSummary(0, 0, 0);
        givenPage(List.of());

        service.getParticipants(1L, ADMIN, null, 0, 20);

        verify(participationRepository).findByEventId(eq(1L), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("페이지 요청을 그대로 전달한다")
    void 페이지를_전달한다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        givenSummary(0, 0, 0);
        givenPage(List.of());

        service.getParticipants(1L, ADMIN, null, 2, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(participationRepository).findByEventId(eq(1L), isNull(), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
    }

    @Test
    @DisplayName("남의 이벤트면 참여 수조차 알려주지 않는다 — 건수만으로도 성과가 새어 나간다")
    void 남의이벤트는_아무것도_읽지않는다() {
        when(ownerCheck.requireOwned(1L, ADMIN))
                .thenThrow(new AccessDeniedException("이벤트 소유 관리자만 사용할 수 있습니다."));

        assertThrows(AccessDeniedException.class,
                () -> service.getParticipants(1L, ADMIN, null, 0, 20));

        verify(participationRepository, never()).findByEventId(any(), any(), any());
        verify(participationRepository, never()).findSummaryByEventId(any());
    }

    @Test
    @DisplayName("참여가 없으면 빈 목록과 0 집계다 — 404 가 아니다")
    void 참여가없으면_빈목록이다() {
        when(ownerCheck.requireOwned(1L, ADMIN)).thenReturn(event());
        givenSummary(0, 0, 0);
        givenPage(List.of());

        EventParticipantListResponse result = service.getParticipants(1L, ADMIN, null, 0, 20);

        assertEquals(0, result.participants().content().size());
        assertEquals(0, result.summary().totalParticipationCount());
        assertEquals(0, result.participants().totalPages());
    }

    // ── 거푸집 ────────────────────────────────────────────────────

    private void givenPage(List<EventParticipation> rows) {
        Page<EventParticipation> page = new PageImpl<>(rows, PageRequest.of(0, 20), rows.size());
        when(participationRepository.findByEventId(eq(1L), any(), any(Pageable.class)))
                .thenReturn(page);
    }

    private void givenSummary(long total, long reward, long pending) {
        ParticipationSummaryProjection counts = mock(ParticipationSummaryProjection.class);
        when(counts.getTotalParticipationCount()).thenReturn(total);
        when(counts.getRewardCount()).thenReturn(reward);
        when(counts.getPendingCount()).thenReturn(pending);
        when(participationRepository.findSummaryByEventId(1L)).thenReturn(counts);
    }

    private EventParticipation participation(
            Long id, User user, Map<String, Object> submitted, Map<String, Object> result) {
        EventParticipation participation =
                EventParticipation.create(event(), user, submitted, result);
        ReflectionTestUtils.setField(participation, "id", id);
        ReflectionTestUtils.setField(participation, "createdAt", OffsetDateTime.now());
        return participation;
    }

    private User user(Long id, String name) {
        User user = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "name", name);
        return user;
    }

    private Event event() {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "id", 1L);
        return event;
    }
}
