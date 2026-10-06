package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.newvent.admin.domain.Admin;
import com.newvent.admin.repository.AdminRepository;
import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventTemplate;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.request.EventUpdateRequest;
import com.newvent.event.dto.response.EventCountsResponse;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventTemplateRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.service.GenerationJob;
import com.newvent.generation.service.GenerationJobStore;
import com.newvent.user.domain.MembershipGrade;

/**
 * 이름·상태·기간 필터와 정렬은 EventRepository.findAdminEvents 의 JPQL 로 옮겨졌다 —
 * 여기서는 EventService 가 파라미터를 그대로 위임하고 결과를 올바르게 매핑하는지만 검증한다.
 * 필터 자체의 동작은 EventRepositoryTest(@DataJpaTest) 에서 확인한다.
 */
class EventServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final EventTemplateRepository eventTemplateRepository = mock(EventTemplateRepository.class);
    private final EventVersionRepository eventVersionRepository = mock(EventVersionRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final GenerationJobStore generationJobStore = mock(GenerationJobStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T01:00:00+09:00"), SEOUL);
    private final EventService eventService =
            new EventService(eventRepository, eventTemplateRepository, eventVersionRepository,
                    adminRepository, generationJobStore, clock);

    @Test
    @DisplayName("목록 조회는 리포지토리 결과를 마감임박과 함께 매핑한다")
    void 관리자_목록_조회에_성공한다() {
        Event event = newEvent(3L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-18T23:59:59+09:00"));
        Page<Event> page = new PageImpl<>(List.of(event), PageRequest.of(0, 10), 1);
        when(eventRepository.findAdminEvents(isNull(), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 10))))
                .thenReturn(page);

        PageResponse<EventSummaryResponse> result = eventService.findAdminEvents(null, null, null, null, 0, 10);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).id()).isEqualTo(3L);
        assertThat(result.content().get(0).closingSoon()).isTrue();
    }

    @Test
    @DisplayName("이름은 소문자 LIKE 패턴으로 변환해서 리포지토리에 전달한다")
    void 이름검색은_LIKE_패턴으로_변환한다() {
        when(eventRepository.findAdminEvents(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        eventService.findAdminEvents("월드컵", null, null, null, 0, 10);

        verify(eventRepository).findAdminEvents(
                eq("%월드컵%"), isNull(), isNull(), isNull(), eq(PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("상태·기간은 그대로 리포지토리에 전달한다")
    void 상태와_기간은_그대로_전달한다() {
        when(eventRepository.findAdminEvents(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        OffsetDateTime from = OffsetDateTime.parse("2026-09-01T00:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-09-30T23:59:59+09:00");

        eventService.findAdminEvents(null, EventStatus.PUBLISHED, from, to, 1, 20);

        verify(eventRepository).findAdminEvents(
                isNull(), eq(EventStatus.PUBLISHED), eq(from), eq(to), eq(PageRequest.of(1, 20)));
    }

    @Test
    @DisplayName("조회 시작일이 종료일보다 늦으면 EVENT400-1 이고 리포지토리는 호출되지 않는다")
    void 조회기간이_역전되면_400을_던진다() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-30T00:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-09-01T00:00:00+09:00");

        assertThatThrownBy(() -> eventService.findAdminEvents(null, null, from, to, 0, 10))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.INVALID_SEARCH_PERIOD.getCode());
        verifyNoInteractions(eventRepository);
    }

    @Test
    @DisplayName("상세 조회는 HTML 과 마감임박을 함께 반환한다")
    void 관리자_상세_조회에_성공한다() {
        Event event = newEvent(3L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-18T23:59:59+09:00"));
        ReflectionTestUtils.setField(event, "title", "지금 긁으면 바로 당첨");
        ReflectionTestUtils.setField(event, "publishedVersion",
                EventVersion.htmlOnly("<h1>지금 긁으면 바로 당첨</h1>"));
        when(eventRepository.findAdminEventById(3L)).thenReturn(Optional.of(event));

        EventDetailResponse detail = eventService.findAdminEvent(3L);

        assertThat(detail.name()).isEqualTo("지금 긁으면 바로 당첨");
        assertThat(detail.closingSoon()).isTrue();
        assertThat(detail.completedHtml()).contains("지금 긁으면 바로 당첨");
    }

    @Test
    @DisplayName("없거나 삭제된 이벤트는 EVENT404-0 이다")
    void 없는_이벤트는_404를_던진다() {
        when(eventRepository.findAdminEventById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.findAdminEvent(999L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("종료 3일 전이 아니면 마감임박이 아니다")
    void 마감임박이_아닌_게시중_이벤트() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        when(eventRepository.findAdminEventById(1L)).thenReturn(Optional.of(event));

        EventDetailResponse detail = eventService.findAdminEvent(1L);

        assertThat(detail.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(detail.closingSoon()).isFalse();
    }

    @Test
    @DisplayName("휴지통 목록 조회는 리포지토리 결과를 매핑한다")
    void 휴지통_목록_조회에_성공한다() {
        Event event = newEvent(99L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-01-10T23:59:59+09:00"));
        ReflectionTestUtils.setField(event, "deletedAt", OffsetDateTime.parse("2026-01-11T00:00:00+09:00"));
        Page<Event> page = new PageImpl<>(List.of(event), PageRequest.of(0, 10), 1);
        when(eventRepository.findDeletedEvents(eq(PageRequest.of(0, 10)))).thenReturn(page);

        PageResponse<EventSummaryResponse> result = eventService.findDeletedEvents(0, 10);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).id()).isEqualTo(99L);
    }

    @Test
    @DisplayName("상태별 건수는 전체/게시중/게시전/종료로 집계된다")
    void 상태별_건수를_집계한다() {
        when(eventRepository.countGroupedByStatus()).thenReturn(List.of(
                new Object[] {EventStatus.PUBLISHED, 3L},
                new Object[] {EventStatus.DRAFT, 2L},
                new Object[] {EventStatus.ENDED, 5L}));

        EventCountsResponse counts = eventService.findEventCounts();

        assertThat(counts.total()).isEqualTo(10L);
        assertThat(counts.published()).isEqualTo(3L);
        assertThat(counts.draft()).isEqualTo(2L);
        assertThat(counts.ended()).isEqualTo(5L);
    }

    @Test
    @DisplayName("이벤트가 하나도 없으면 전부 0건이다")
    void 이벤트가_없으면_전부_0건이다() {
        when(eventRepository.countGroupedByStatus()).thenReturn(List.of());

        EventCountsResponse counts = eventService.findEventCounts();

        assertThat(counts.total()).isZero();
        assertThat(counts.published()).isZero();
        assertThat(counts.draft()).isZero();
        assertThat(counts.ended()).isZero();
    }

    @Test
    @DisplayName("게시 중인 이벤트는 삭제할 수 없다")
    void 게시중인_이벤트는_삭제할_수_없다() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.delete(1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.PUBLISHED_EVENT_DELETE_FORBIDDEN.getCode());
        assertThat(event.deleted()).isFalse();
    }

    @Test
    @DisplayName("DRAFT/ENDED 이벤트는 휴지통으로 이동한다")
    void 이벤트를_삭제하면_deletedAt이_설정된다() {
        Event event = newEvent(2L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-07-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-07-31T23:59:59+09:00"));
        when(eventRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(event));

        eventService.delete(2L);

        assertThat(event.deleted()).isTrue();
    }

    @Test
    @DisplayName("없거나 이미 삭제된 이벤트를 삭제하려 하면 EVENT404-0 이다")
    void 없는_이벤트_삭제는_404를_던진다() {
        when(eventRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.delete(999L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("생성 작업이 진행 중인 이벤트는 삭제할 수 없다")
    void 생성중인_이벤트는_삭제할_수_없다() {
        Event event = newEvent(2L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-07-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-07-31T23:59:59+09:00"));
        when(eventRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(event));
        when(generationJobStore.ofEvent(2L)).thenReturn(Optional.of(new GenerationJob(2L)));

        assertThatThrownBy(() -> eventService.delete(2L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_GENERATING_DELETE_FORBIDDEN.getCode());
        assertThat(event.deleted()).isFalse();
    }

    @Test
    @DisplayName("휴지통에서 영구 삭제하면 리포지토리 delete 가 호출된다")
    void 영구_삭제에_성공한다() {
        Event event = newEvent(99L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-01-10T23:59:59+09:00"));
        ReflectionTestUtils.setField(event, "deletedAt", OffsetDateTime.parse("2026-01-11T00:00:00+09:00"));
        when(eventRepository.findByIdAndDeletedAtIsNotNull(99L)).thenReturn(Optional.of(event));

        eventService.hardDelete(99L);

        verify(eventRepository).delete(event);
    }

    @Test
    @DisplayName("삭제되지 않은 이벤트를 영구 삭제하려 하면 EVENT404-0 이다")
    void 삭제되지_않은_이벤트_영구삭제는_404를_던진다() {
        when(eventRepository.findByIdAndDeletedAtIsNotNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.hardDelete(1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("복구하면 deletedAt 이 해제되고 상세를 반환한다")
    void 이벤트를_복구한다() {
        Event event = newEvent(99L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-01-10T23:59:59+09:00"));
        ReflectionTestUtils.setField(event, "deletedAt", OffsetDateTime.parse("2026-01-11T00:00:00+09:00"));
        when(eventRepository.findByIdAndDeletedAtIsNotNull(99L)).thenReturn(Optional.of(event));

        EventDetailResponse restored = eventService.restore(99L);

        assertThat(event.deleted()).isFalse();
        assertThat(restored.id()).isEqualTo(99L);
    }

    @Test
    @DisplayName("삭제되지 않은 이벤트를 복구하려 하면 EVENT404-0 이다")
    void 삭제되지_않은_이벤트_복구는_404를_던진다() {
        when(eventRepository.findByIdAndDeletedAtIsNotNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.restore(1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("게시하면 상태가 PUBLISHED 로 바뀌고 선택한 버전이 체크포인트로 표시된다")
    void 게시에_성공한다() {
        Event event = newEvent(1L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        EventVersion version = newVersion(10L, false);
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(10L, 1L)).thenReturn(Optional.of(version));

        EventDetailResponse published = eventService.publish(1L, 10L);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getPublishedVersion()).isSameAs(version);
        assertThat(version.isCheckpoint()).isTrue();
        assertThat(published.status()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("게시 중인 이벤트를 다른 버전으로 다시 게시하면 게시 버전이 교체된다")
    void 재게시로_버전을_교체한다() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        EventVersion oldVersion = newVersion(10L, true);
        ReflectionTestUtils.setField(event, "publishedVersion", oldVersion);
        EventVersion newVersion = newVersion(11L, false);
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(11L, 1L)).thenReturn(Optional.of(newVersion));

        eventService.publish(1L, 11L);

        assertThat(event.getPublishedVersion()).isSameAs(newVersion);
        assertThat(newVersion.isCheckpoint()).isTrue();
    }

    @Test
    @DisplayName("게시를 내렸다가 같은 버전으로 다시 게시할 수 있고 저장 지점 표시는 유지된다")
    void 내린_이벤트를_같은_버전으로_다시_게시한다() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        EventVersion version = newVersion(10L, true);
        ReflectionTestUtils.setField(event, "publishedVersion", version);
        when(eventRepository.findAdminEventById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(10L, 1L)).thenReturn(Optional.of(version));

        eventService.unpublish(1L);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getPublishedVersion()).isNull();

        EventDetailResponse republished = eventService.publish(1L, 10L);

        assertThat(republished.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getPublishedVersion()).isSameAs(version);
        assertThat(version.isCheckpoint()).isTrue();
    }

    @Test
    @DisplayName("게시를 내린 뒤 다른 버전으로 다시 게시하면 그 버전이 게시 버전이 되고 저장 지점으로 표시된다")
    void 내린_이벤트를_다른_버전으로_다시_게시한다() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        EventVersion oldVersion = newVersion(10L, true);
        ReflectionTestUtils.setField(event, "publishedVersion", oldVersion);
        EventVersion newVersion = newVersion(11L, false);
        when(eventRepository.findAdminEventById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(11L, 1L)).thenReturn(Optional.of(newVersion));

        eventService.unpublish(1L);
        eventService.publish(1L, 11L);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getPublishedVersion()).isSameAs(newVersion);
        assertThat(newVersion.isCheckpoint()).isTrue();
        assertThat(oldVersion.isCheckpoint()).isTrue();
    }

    @Test
    @DisplayName("내렸다가 다시 게시해도 이미 보낸 알림 표시는 지워지지 않아 알림이 재발송되지 않는다")
    void 재게시해도_알림_표시는_유지된다() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        EventVersion version = newVersion(10L, true);
        ReflectionTestUtils.setField(event, "publishedVersion", version);
        OffsetDateTime startNotifiedAt = OffsetDateTime.parse("2026-09-16T00:01:00+09:00");
        event.markStartNotified(startNotifiedAt);
        when(eventRepository.findAdminEventById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(10L, 1L)).thenReturn(Optional.of(version));

        eventService.unpublish(1L);
        eventService.publish(1L, 10L);

        assertThat(event.getStartNotifiedAt()).isEqualTo(startNotifiedAt);
    }

    @Test
    @DisplayName("종료된 이벤트는 게시할 수 없다")
    void 종료된_이벤트는_게시할_수_없다() {
        Event event = newEvent(1L, EventStatus.ENDED,
                OffsetDateTime.parse("2026-06-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-08-31T23:59:59+09:00"));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.publish(1L, 10L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_ENDED_PUBLISH_FORBIDDEN.getCode());
        verifyNoInteractions(eventVersionRepository);
    }

    @Test
    @DisplayName("해당 이벤트의 버전이 아니면 EVENT404-3 이다")
    void 없는_버전으로_게시하면_404를_던진다() {
        Event event = newEvent(1L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-09-16T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"));
        when(eventRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.publish(1L, 999L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.VERSION_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("없거나 삭제된 이벤트를 게시하려 하면 EVENT404-0 이다")
    void 없는_이벤트_게시는_404를_던진다() {
        when(eventRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.publish(999L, 10L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
        verifyNoInteractions(eventVersionRepository);
    }

    @Test
    @DisplayName("생성은 DRAFT 로 저장되고 로그인한 관리자를 소유자로 지정한다")
    void 이벤트_생성에_성공한다() {
        EventCreateRequest request = new EventCreateRequest(
                "테스트 이벤트",
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"),
                "sports_cheer",
                MembershipGrade.BEST);
        when(eventTemplateRepository.findByKey("sports_cheer")).thenReturn(
                Optional.of(EventTemplate.seed("sports_cheer", "스포츠 응원", "설명", "<html/>", true)));
        Admin admin = BeanUtils.instantiateClass(Admin.class);
        when(adminRepository.getReferenceById(7L)).thenReturn(admin);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventDetailResponse created = eventService.create(7L, request);

        assertThat(created.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(created.template()).isEqualTo("sports_cheer");
        assertThat(created.grade()).isEqualTo(MembershipGrade.BEST);
        assertThat(created.completedHtml()).isNull();
        assertThat(created.closingSoon()).isFalse();
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    @DisplayName("등급을 보내지 않으면 NORMAL 로 저장한다")
    void 등급_누락시_NORMAL_로_저장한다() {
        EventCreateRequest request = new EventCreateRequest(
                "등급 없음",
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"),
                null,
                null);
        when(adminRepository.getReferenceById(7L)).thenReturn(BeanUtils.instantiateClass(Admin.class));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventDetailResponse created = eventService.create(7L, request);

        assertThat(created.grade()).isEqualTo(MembershipGrade.NORMAL);
    }

    @Test
    @DisplayName("종료일시가 시작일시 이전이면 EVENT400-0 이다")
    void 잘못된_기간은_400을_던진다() {
        EventCreateRequest request = new EventCreateRequest(
                "잘못된 기간",
                OffsetDateTime.parse("2026-10-15T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                null,
                null);

        assertThatThrownBy(() -> eventService.create(7L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.INVALID_PERIOD.getCode());
        verifyNoInteractions(eventRepository, adminRepository);
    }

    @Test
    @DisplayName("없는 템플릿 키는 EVENT404-1 이다")
    void 없는_템플릿은_404를_던진다() {
        EventCreateRequest request = new EventCreateRequest(
                "템플릿 없음",
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"),
                "없는키",
                null);
        when(eventTemplateRepository.findByKey("없는키")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.create(7L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.TEMPLATE_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("수정은 보낸 필드만 바꾸고 나머지는 유지한다")
    void 이름만_수정하면_나머지는_유지된다() {
        Event event = draftWorldCupEvent();

        EventDetailResponse updated = eventService.update(1L, 2L, new EventUpdateRequest(
                "  월드컵 승부 예측  ", null, null, null, null));

        assertThat(updated.name()).isEqualTo("월드컵 승부 예측");
        assertThat(updated.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(updated.grade()).isEqualTo(MembershipGrade.EXCELLENT);
        assertThat(updated.startAt()).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00+09:00"));
        assertThat(updated.endAt()).isEqualTo(OffsetDateTime.parse("2026-07-31T23:59:59+09:00"));
        assertThat(event.getTitle()).isEqualTo("월드컵 승부 예측");
        verify(eventRepository).flush();
    }

    @Test
    @DisplayName("기간·템플릿·등급을 함께 수정한다")
    void 기간과_템플릿과_등급을_수정한다() {
        draftWorldCupEvent();
        when(eventTemplateRepository.findByKey("sports_cheer")).thenReturn(
                Optional.of(EventTemplate.seed("sports_cheer", "스포츠 응원", "설명", "<html/>", true)));

        EventDetailResponse updated = eventService.update(1L, 2L, new EventUpdateRequest(
                null,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-10T23:59:59+09:00"),
                "sports_cheer",
                MembershipGrade.BEST));

        assertThat(updated.startAt()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"));
        assertThat(updated.endAt()).isEqualTo(OffsetDateTime.parse("2026-10-10T23:59:59+09:00"));
        assertThat(updated.template()).isEqualTo("sports_cheer");
        assertThat(updated.grade()).isEqualTo(MembershipGrade.BEST);
        assertThat(updated.name()).isEqualTo("월드컵 스코어 맞추기");
    }

    @Test
    @DisplayName("종료일을 바꾸면 마감임박 알림 발송 표시가 지워진다")
    void 종료일을_바꾸면_마감임박_알림_표시가_지워진다() {
        Event event = draftWorldCupEvent();
        ReflectionTestUtils.setField(event, "startNotifiedAt", OffsetDateTime.parse("2026-07-01T00:00:00+09:00"));
        ReflectionTestUtils.setField(event, "closingSoonNotifiedAt", OffsetDateTime.parse("2026-07-29T00:00:00+09:00"));

        eventService.update(1L, 2L, new EventUpdateRequest(
                null, null, OffsetDateTime.parse("2026-08-15T00:00:00+09:00"), null, null));

        assertThat(event.getClosingSoonNotifiedAt()).isNull();
        // startDate는 안 바뀌었으니 시작 알림 표시는 그대로 남아야 한다.
        assertThat(event.getStartNotifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("날짜를 안 바꾸면 알림 발송 표시도 그대로 남는다")
    void 날짜를_안_바꾸면_알림_표시가_유지된다() {
        Event event = draftWorldCupEvent();
        ReflectionTestUtils.setField(event, "startNotifiedAt", OffsetDateTime.parse("2026-07-01T00:00:00+09:00"));
        ReflectionTestUtils.setField(event, "closingSoonNotifiedAt", OffsetDateTime.parse("2026-07-29T00:00:00+09:00"));

        eventService.update(1L, 2L, new EventUpdateRequest("이름만 변경", null, null, null, null));

        assertThat(event.getStartNotifiedAt()).isNotNull();
        assertThat(event.getClosingSoonNotifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("templateKey 가 빈 문자열이면 템플릿을 해제한다")
    void 빈_템플릿_키는_템플릿을_해제한다() {
        Event event = draftWorldCupEvent();
        ReflectionTestUtils.setField(event, "template",
                EventTemplate.seed("sports_cheer", "스포츠 응원", "설명", "<html/>", true));

        EventDetailResponse updated = eventService.update(1L, 2L, new EventUpdateRequest(null, null, null, "", null));

        assertThat(updated.template()).isNull();
        verifyNoInteractions(eventTemplateRepository);
    }

    @Test
    @DisplayName("종료일시만 보내도 기존 시작일시와 비교해 EVENT400-0 을 던진다")
    void 수정_후_기간이_역전되면_400을_던진다() {
        Event event = draftWorldCupEvent();
        EventUpdateRequest request = new EventUpdateRequest(
                null, null, OffsetDateTime.parse("2026-06-30T00:00:00+09:00"), null, null);

        assertThatThrownBy(() -> eventService.update(1L, 2L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.INVALID_PERIOD.getCode());
        assertThat(event.getEndDate()).isEqualTo(OffsetDateTime.parse("2026-07-31T23:59:59+09:00"));
    }

    @Test
    @DisplayName("종료(ENDED)된 이벤트는 수정할 수 없고 EVENT409-1 을 던진다")
    void 종료된_이벤트는_수정할_수_없다() {
        Event event = newEvent(6L, EventStatus.ENDED,
                OffsetDateTime.parse("2026-08-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-08-31T23:59:59+09:00"));
        ReflectionTestUtils.setField(event, "title", "여름 데이터 대방출");
        when(eventRepository.findAdminEventById(6L)).thenReturn(Optional.of(event));
        EventUpdateRequest request = new EventUpdateRequest("이름 변경", null, null, null, null);

        assertThatThrownBy(() -> eventService.update(1L, 6L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_ENDED_NOT_EDITABLE.getCode());
        assertThat(event.getTitle()).isEqualTo("여름 데이터 대방출");
    }

    @Test
    @DisplayName("게시 중이어도 기존 종료일시가 지났으면 수정할 수 없고 EVENT409-1 을 던진다")
    void 종료일시가_지난_게시_이벤트는_수정할_수_없다() {
        Event event = newEvent(5L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-15T23:59:59+09:00"));
        when(eventRepository.findAdminEventById(5L)).thenReturn(Optional.of(event));
        EventUpdateRequest request = new EventUpdateRequest(
                null, null, OffsetDateTime.parse("2026-09-30T23:59:59+09:00"), null, null);

        assertThatThrownBy(() -> eventService.update(1L, 5L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_ENDED_NOT_EDITABLE.getCode());
        assertThat(event.getEndDate()).isEqualTo(OffsetDateTime.parse("2026-09-15T23:59:59+09:00"));
    }

    @Test
    @DisplayName("진행 중인 게시 이벤트는 정보·기간을 수정할 수 있다")
    void 진행중인_게시_이벤트는_수정할_수_있다() {
        Event event = publishedEventWithTemplate();

        EventDetailResponse updated = eventService.update(1L, 4L, new EventUpdateRequest(
                "추석 특가", null, OffsetDateTime.parse("2026-10-05T23:59:59+09:00"), null, null));

        assertThat(updated.name()).isEqualTo("추석 특가");
        assertThat(updated.endAt()).isEqualTo(OffsetDateTime.parse("2026-10-05T23:59:59+09:00"));
        assertThat(event.templateCode()).isEqualTo("flash_sale");
    }

    @Test
    @DisplayName("기간이 지난 DRAFT 는 게시 전이라 기간을 다시 잡을 수 있다")
    void 기간이_지난_DRAFT는_기간을_다시_잡을_수_있다() {
        draftWorldCupEvent();

        EventDetailResponse updated = eventService.update(1L, 2L, new EventUpdateRequest(
                null,
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-31T23:59:59+09:00"),
                null,
                null));

        assertThat(updated.startAt()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"));
        assertThat(updated.endAt()).isEqualTo(OffsetDateTime.parse("2026-10-31T23:59:59+09:00"));
    }

    @Test
    @DisplayName("게시 중인 이벤트의 템플릿을 다른 템플릿으로 바꾸면 EVENT409-4 를 던진다")
    void 게시중_템플릿_변경은_409를_던진다() {
        Event event = publishedEventWithTemplate();
        EventUpdateRequest request = new EventUpdateRequest(null, null, null, "sports_cheer", null);

        assertThatThrownBy(() -> eventService.update(1L, 4L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.PUBLISHED_EVENT_TEMPLATE_NOT_EDITABLE.getCode());
        assertThat(event.templateCode()).isEqualTo("flash_sale");
        verifyNoInteractions(eventTemplateRepository);
    }

    @Test
    @DisplayName("게시 중인 이벤트의 템플릿을 해제하면 EVENT409-4 를 던진다")
    void 게시중_템플릿_해제는_409를_던진다() {
        Event event = publishedEventWithTemplate();
        EventUpdateRequest request = new EventUpdateRequest(null, null, null, "", null);

        assertThatThrownBy(() -> eventService.update(1L, 4L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.PUBLISHED_EVENT_TEMPLATE_NOT_EDITABLE.getCode());
        assertThat(event.templateCode()).isEqualTo("flash_sale");
    }

    @Test
    @DisplayName("게시 중이어도 지금과 같은 템플릿 키를 보내면 수정된다")
    void 게시중_같은_템플릿_키는_허용한다() {
        publishedEventWithTemplate();
        when(eventTemplateRepository.findByKey("flash_sale")).thenReturn(
                Optional.of(EventTemplate.seed("flash_sale", "타임 특가", "설명", "<html/>", true)));

        EventDetailResponse updated = eventService.update(1L, 4L, new EventUpdateRequest(
                "추석 특가", null, null, " flash_sale ", null));

        assertThat(updated.name()).isEqualTo("추석 특가");
        assertThat(updated.template()).isEqualTo("flash_sale");
    }

    @Test
    @DisplayName("없거나 삭제된 이벤트 수정은 EVENT404-0 이다")
    void 없는_이벤트_수정은_404를_던진다() {
        when(eventRepository.findAdminEventById(999L)).thenReturn(Optional.empty());
        EventUpdateRequest request = new EventUpdateRequest("이름 변경", null, null, null, null);

        assertThatThrownBy(() -> eventService.update(1L, 999L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("없는 템플릿 키로 수정하면 EVENT404-1 이다")
    void 없는_템플릿으로_수정하면_404를_던진다() {
        draftWorldCupEvent();
        when(eventTemplateRepository.findByKey("없는키")).thenReturn(Optional.empty());
        EventUpdateRequest request = new EventUpdateRequest(null, null, null, "없는키", null);

        assertThatThrownBy(() -> eventService.update(1L, 2L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.TEMPLATE_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("게시 중인 이벤트를 종료하면 ENDED 가 되고 게시 버전은 유지된다")
    void 게시중_이벤트를_종료한다() {
        Event event = publishedEventWithTemplate();
        EventVersion version = BeanUtils.instantiateClass(EventVersion.class);
        ReflectionTestUtils.setField(event, "publishedVersion", version);

        EventDetailResponse ended = eventService.changeStatus(4L, EventStatus.ENDED);

        assertThat(ended.status()).isEqualTo(EventStatus.ENDED);
        assertThat(ended.closingSoon()).isFalse();
        assertThat(event.getStatus()).isEqualTo(EventStatus.ENDED);
        assertThat(event.getPublishedVersion()).isSameAs(version);
        verify(eventRepository).flush();
    }

    @Test
    @DisplayName("DRAFT 이벤트는 종료할 수 없고 EVENT409-6 을 던진다")
    void DRAFT_이벤트는_종료할_수_없다() {
        Event event = draftWorldCupEvent();

        assertThatThrownBy(() -> eventService.changeStatus(2L, EventStatus.ENDED))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_ENDABLE.getCode());
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    @DisplayName("이미 종료된 이벤트를 다시 종료하면 EVENT409-6 을 던진다")
    void 종료된_이벤트는_다시_종료할_수_없다() {
        Event event = newEvent(6L, EventStatus.ENDED,
                OffsetDateTime.parse("2026-08-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-08-31T23:59:59+09:00"));
        when(eventRepository.findAdminEventById(6L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.changeStatus(6L, EventStatus.ENDED))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_ENDABLE.getCode());
    }

    @Test
    @DisplayName("상태 변경으로 PUBLISHED·DRAFT 를 요청하면 EVENT400-2 를 던진다")
    void 종료가_아닌_상태_요청은_400을_던진다() {
        Event event = publishedEventWithTemplate();

        for (EventStatus target : List.of(EventStatus.PUBLISHED, EventStatus.DRAFT)) {
            assertThatThrownBy(() -> eventService.changeStatus(4L, target))
                    .isInstanceOf(EventException.class)
                    .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                    .isEqualTo(EventErrorCode.UNSUPPORTED_STATUS_CHANGE.getCode());
        }
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("없거나 삭제된 이벤트 종료는 EVENT404-0 이다")
    void 없는_이벤트_종료는_404를_던진다() {
        when(eventRepository.findAdminEventById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.changeStatus(999L, EventStatus.ENDED))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("게시 중인 이벤트의 게시를 내리면 DRAFT 가 되고 게시 버전이 해제된다")
    void 게시중_이벤트의_게시를_내린다() {
        Event event = publishedEventWithTemplate();
        EventVersion version = BeanUtils.instantiateClass(EventVersion.class);
        ReflectionTestUtils.setField(event, "publishedVersion", version);

        EventDetailResponse unpublished = eventService.unpublish(4L);

        assertThat(unpublished.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(unpublished.closingSoon()).isFalse();
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getPublishedVersion()).isNull();
        verify(eventRepository).flush();
    }

    @Test
    @DisplayName("게시를 내려도 시작·마감임박 알림 표시는 그대로다")
    void 게시를_내려도_알림_표시는_유지된다() {
        Event event = publishedEventWithTemplate();
        OffsetDateTime startNotifiedAt = OffsetDateTime.parse("2026-09-01T00:00:00+09:00");
        OffsetDateTime closingSoonNotifiedAt = OffsetDateTime.parse("2026-09-28T00:00:00+09:00");
        event.markStartNotified(startNotifiedAt);
        event.markClosingSoonNotified(closingSoonNotifiedAt);

        eventService.unpublish(4L);

        assertThat(event.getStartNotifiedAt()).isEqualTo(startNotifiedAt);
        assertThat(event.getClosingSoonNotifiedAt()).isEqualTo(closingSoonNotifiedAt);
    }

    @Test
    @DisplayName("DRAFT 이벤트의 게시 내리기는 EVENT409-7 을 던진다")
    void DRAFT_이벤트는_게시를_내릴_수_없다() {
        Event event = draftWorldCupEvent();

        assertThatThrownBy(() -> eventService.unpublish(2L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_UNPUBLISHABLE.getCode());
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    @DisplayName("종료된 이벤트의 게시 내리기는 EVENT409-7 을 던지고 상태는 ENDED 로 남는다")
    void 종료된_이벤트는_게시를_내릴_수_없다() {
        Event event = newEvent(6L, EventStatus.ENDED,
                OffsetDateTime.parse("2026-08-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-08-31T23:59:59+09:00"));
        when(eventRepository.findAdminEventById(6L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.unpublish(6L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_UNPUBLISHABLE.getCode());
        assertThat(event.getStatus()).isEqualTo(EventStatus.ENDED);
    }

    @Test
    @DisplayName("종료 시각이 지났지만 아직 ENDED 로 바뀌기 전인 이벤트는 게시를 내릴 수 없다 — EVENT409-1")
    void 종료_시각이_지난_게시중_이벤트는_게시를_내릴_수_없다() {
        // clock 은 2026-09-16T01:00+09:00. 종료일이 그 전이면 자동 종료 스케줄러만 아직 안 돈 상태
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-15T23:59:59+09:00"));
        EventVersion version = newVersion(10L, true);
        ReflectionTestUtils.setField(event, "publishedVersion", version);
        when(eventRepository.findAdminEventById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.unpublish(1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_ENDED_NOT_EDITABLE.getCode());
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(event.getPublishedVersion()).isSameAs(version);
        verify(eventRepository, never()).flush();
    }

    @Test
    @DisplayName("종료 시각과 현재 시각이 같으면 이미 종료로 보고 게시를 내릴 수 없다")
    void 종료_시각과_같은_순간에는_게시를_내릴_수_없다() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-16T01:00:00+09:00"));
        when(eventRepository.findAdminEventById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventService.unpublish(1L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_ENDED_NOT_EDITABLE.getCode());
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("종료 1초 전까지는 게시를 내릴 수 있다")
    void 종료_직전에는_게시를_내릴_수_있다() {
        Event event = newEvent(1L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-16T01:00:01+09:00"));
        when(eventRepository.findAdminEventById(1L)).thenReturn(Optional.of(event));

        EventDetailResponse unpublished = eventService.unpublish(1L);

        assertThat(unpublished.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    @DisplayName("없거나 삭제된 이벤트의 게시 내리기는 EVENT404-0 이다")
    void 없는_이벤트의_게시_내리기는_404를_던진다() {
        when(eventRepository.findAdminEventById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.unpublish(999L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    void 다른_관리자_템플릿으로_이벤트를_생성할_수_없다() {
        Admin other = BeanUtils.instantiateClass(Admin.class);
        ReflectionTestUtils.setField(other, "id", 99L);
        EventTemplate custom = EventTemplate.custom("custom_other", other, "등록본", null, "<div/>");
        when(eventTemplateRepository.findByKey("custom_other")).thenReturn(Optional.of(custom));
        EventCreateRequest request = new EventCreateRequest("새 이벤트",
                OffsetDateTime.parse("2026-10-10T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-20T00:00:00+09:00"), "custom_other", MembershipGrade.NORMAL);
        assertThatThrownBy(() -> eventService.create(1L, request)).isInstanceOf(EventException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void 다른_관리자가_이벤트의_템플릿을_변경할_수_없다() {
        draftWorldCupEvent();
        assertThatThrownBy(() -> eventService.update(9L, 2L,
                new EventUpdateRequest(null, null, null, "sports_cheer", null)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(eventTemplateRepository);
        verify(eventRepository, never()).flush();
    }

    private Event draftWorldCupEvent() {
        Event event = newEvent(2L, EventStatus.DRAFT,
                OffsetDateTime.parse("2026-07-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-07-31T23:59:59+09:00"));
        ReflectionTestUtils.setField(event, "title", "월드컵 스코어 맞추기");
        ReflectionTestUtils.setField(event, "grade", MembershipGrade.EXCELLENT);
        when(eventRepository.findAdminEventById(2L)).thenReturn(Optional.of(event));
        return event;
    }

    private Event publishedEventWithTemplate() {
        Event event = newEvent(4L, EventStatus.PUBLISHED,
                OffsetDateTime.parse("2026-09-10T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-30T23:59:59+09:00"));
        ReflectionTestUtils.setField(event, "title", "가을 타임 특가");
        ReflectionTestUtils.setField(event, "template",
                EventTemplate.seed("flash_sale", "타임 특가", "설명", "<html/>", true));
        when(eventRepository.findAdminEventById(4L)).thenReturn(Optional.of(event));
        return event;
    }

    private Event newEvent(Long id, EventStatus status, OffsetDateTime startDate, OffsetDateTime endDate) {
        Event event = BeanUtils.instantiateClass(Event.class);
        ReflectionTestUtils.setField(event, "id", id);
        Admin owner = BeanUtils.instantiateClass(Admin.class);
        ReflectionTestUtils.setField(owner, "id", 1L);
        ReflectionTestUtils.setField(event, "ownerAdmin", owner);
        ReflectionTestUtils.setField(event, "status", status);
        ReflectionTestUtils.setField(event, "startDate", startDate);
        ReflectionTestUtils.setField(event, "endDate", endDate);
        ReflectionTestUtils.setField(event, "grade", MembershipGrade.NORMAL);
        return event;
    }

    private EventVersion newVersion(Long id, boolean checkpoint) {
        EventVersion version = BeanUtils.instantiateClass(EventVersion.class);
        ReflectionTestUtils.setField(version, "id", id);
        ReflectionTestUtils.setField(version, "checkpoint", checkpoint);
        return version;
    }
}
