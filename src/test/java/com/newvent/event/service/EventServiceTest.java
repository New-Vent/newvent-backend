package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.common.response.PageResponse;
import com.newvent.event.domain.EventStatus;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.event.dto.EventCreateRequest;
import com.newvent.event.dto.EventDetailResponse;
import com.newvent.event.dto.EventSummaryResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.InMemoryEventRepository;
import com.newvent.event.repository.InMemoryEventTemplateRepository;

class EventServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private EventService eventService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T01:00:00+09:00"), SEOUL);
        eventService = new EventService(
                new InMemoryEventRepository(),
                new InMemoryEventTemplateRepository(),
                clock);
    }

    @Test
    @DisplayName("관리자 목록은 삭제되지 않은 이벤트를 수정일 내림차순으로 반환한다")
    void 관리자_목록_조회에_성공한다() {
        PageResponse<EventSummaryResponse> page = eventService.findAdminEvents(null, null, null, null, 0, 10);

        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.content()).extracting(EventSummaryResponse::id)
                .containsExactly(1L, 3L, 5L, 6L, 4L, 2L);
        assertThat(page.content().get(0).closingSoon()).isFalse();
        assertThat(page.content().get(1).id()).isEqualTo(3L);
        assertThat(page.content().get(1).closingSoon()).isTrue();
    }

    @Test
    @DisplayName("이름 검색은 대소문자 없이 포함 여부로 필터한다")
    void 이름_검색에_성공한다() {
        PageResponse<EventSummaryResponse> page = eventService.findAdminEvents("월드컵", null, null, null, 0, 10);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).name()).isEqualTo("월드컵 스코어 맞추기");
    }

    @Test
    @DisplayName("상태 필터는 PUBLISHED 만 남긴다")
    void 상태_필터에_성공한다() {
        PageResponse<EventSummaryResponse> page =
                eventService.findAdminEvents(null, EventStatus.PUBLISHED, null, null, 0, 10);

        assertThat(page.content()).extracting(EventSummaryResponse::status)
                .containsOnly(EventStatus.PUBLISHED);
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("기간 필터는 구간이 겹치는 이벤트만 남긴다")
    void 기간_필터에_성공한다() {
        PageResponse<EventSummaryResponse> page = eventService.findAdminEvents(
                null, null,
                OffsetDateTime.parse("2026-09-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-09-30T23:59:59+09:00"),
                0, 10);

        assertThat(page.content()).extracting(EventSummaryResponse::id)
                .containsExactlyInAnyOrder(1L, 3L, 4L)
                .doesNotContain(2L, 5L, 6L);
    }

    @Test
    @DisplayName("상세 조회는 HTML 과 마감임박을 함께 반환한다")
    void 관리자_상세_조회에_성공한다() {
        EventDetailResponse detail = eventService.findAdminEvent(3L);

        assertThat(detail.name()).isEqualTo("지금 긁으면 바로 당첨");
        assertThat(detail.closingSoon()).isTrue();
        assertThat(detail.completedHtml()).contains("지금 긁으면 바로 당첨");
    }

    @Test
    @DisplayName("없거나 삭제된 이벤트는 EVENT404-0 이다")
    void 없는_이벤트는_404를_던진다() {
        assertThatThrownBy(() -> eventService.findAdminEvent(999L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());

        assertThatThrownBy(() -> eventService.findAdminEvent(99L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("종료 3일 전이 아니면 마감임박이 아니다")
    void 마감임박이_아닌_게시중_이벤트() {
        EventDetailResponse detail = eventService.findAdminEvent(1L);

        assertThat(detail.status()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(detail.closingSoon()).isFalse();
    }

    @Test
    @DisplayName("생성은 DRAFT 로 저장되고 조회된다")
    void 이벤트_생성에_성공한다() {
        EventCreateRequest request = new EventCreateRequest(
                "테스트 이벤트",
                OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-10-15T23:59:59+09:00"),
                "sports_cheer",
                List.of(MembershipGrade.NORMAL));

        EventDetailResponse created = eventService.create(request);

        assertThat(created.id()).isEqualTo(100L);
        assertThat(created.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(created.template()).isEqualTo("sports_cheer");
        assertThat(created.completedHtml()).isNull();
        assertThat(created.closingSoon()).isFalse();
        assertThat(eventService.findAdminEvent(created.id()).name()).isEqualTo("테스트 이벤트");
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

        assertThatThrownBy(() -> eventService.create(request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.INVALID_PERIOD.getCode());
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

        assertThatThrownBy(() -> eventService.create(request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.TEMPLATE_NOT_FOUND.getCode());
    }
}
