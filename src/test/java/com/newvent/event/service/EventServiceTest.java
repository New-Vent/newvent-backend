package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.newvent.event.domain.EventStatus;
import com.newvent.event.dto.request.EventCreateRequest;
import com.newvent.event.dto.request.EventUpdateRequest;
import com.newvent.event.dto.response.EventDetailResponse;
import com.newvent.event.dto.response.EventSummaryResponse;
import com.newvent.event.dto.response.PageResponse;
import com.newvent.event.exception.EventErrorCode;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.InMemoryEventRepository;
import com.newvent.event.repository.InMemoryEventTemplateRepository;
import com.newvent.user.domain.MembershipGrade;

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
    @DisplayName("조회 시작일이 종료일보다 늦으면 EVENT400-1 이다")
    void 조회기간이_역전되면_400을_던진다() {
        OffsetDateTime from = OffsetDateTime.parse("2026-09-30T00:00:00+09:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-09-01T00:00:00+09:00");

        assertThatThrownBy(() -> eventService.findAdminEvents(null, null, from, to, 0, 10))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.INVALID_SEARCH_PERIOD.getCode());
    }

    @Test
    @DisplayName("조회 시작일과 종료일이 같으면 허용한다")
    void 조회기간이_같은_날이면_허용한다() {
        OffsetDateTime same = OffsetDateTime.parse("2026-09-17T00:00:00+09:00");

        PageResponse<EventSummaryResponse> page = eventService.findAdminEvents(null, null, same, same, 0, 10);

        assertThat(page.content()).extracting(EventSummaryResponse::id).contains(1L, 3L, 4L);
    }

    @Test
    @DisplayName("page * size 가 int 범위를 넘어도 빈 페이지를 반환한다")
    void 큰_페이지_번호는_빈_페이지를_반환한다() {
        PageResponse<EventSummaryResponse> page =
                eventService.findAdminEvents(null, null, null, null, Integer.MAX_VALUE, 50);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(6);
    }

    @Test
    @DisplayName("목록 크기를 넘는 페이지는 빈 페이지를 반환한다")
    void 범위를_넘는_페이지는_빈_페이지를_반환한다() {
        PageResponse<EventSummaryResponse> page = eventService.findAdminEvents(null, null, null, null, 1, 10);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(6);
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
                MembershipGrade.BEST);

        EventDetailResponse created = eventService.create(request);

        assertThat(created.id()).isEqualTo(100L);
        assertThat(created.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(created.template()).isEqualTo("sports_cheer");
        assertThat(created.grade()).isEqualTo(MembershipGrade.BEST);
        assertThat(created.completedHtml()).isNull();
        assertThat(created.closingSoon()).isFalse();
        assertThat(eventService.findAdminEvent(created.id()).grade()).isEqualTo(MembershipGrade.BEST);
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

        EventDetailResponse created = eventService.create(request);

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

    @Test
    @DisplayName("수정은 보낸 필드만 바꾸고 나머지는 유지한다")
    void 이름만_수정하면_나머지는_유지된다() {
        EventDetailResponse updated = eventService.update(2L, new EventUpdateRequest(
                "  월드컵 승부 예측  ", null, null, null, null));

        assertThat(updated.name()).isEqualTo("월드컵 승부 예측");
        assertThat(updated.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(updated.grade()).isEqualTo(MembershipGrade.EXCELLENT);
        assertThat(updated.startAt()).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00+09:00"));
        assertThat(updated.endAt()).isEqualTo(OffsetDateTime.parse("2026-07-31T23:59:59+09:00"));
        assertThat(updated.updatedAt()).isEqualTo(OffsetDateTime.parse("2026-09-16T01:00:00+09:00"));
        assertThat(eventService.findAdminEvent(2L).name()).isEqualTo("월드컵 승부 예측");
    }

    @Test
    @DisplayName("기간·템플릿·등급을 함께 수정한다")
    void 기간과_템플릿과_등급을_수정한다() {
        EventDetailResponse updated = eventService.update(2L, new EventUpdateRequest(
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
    @DisplayName("templateKey 가 빈 문자열이면 템플릿을 해제한다")
    void 빈_템플릿_키는_템플릿을_해제한다() {
        eventService.update(2L, new EventUpdateRequest(null, null, null, "sports_cheer", null));

        EventDetailResponse updated = eventService.update(2L, new EventUpdateRequest(null, null, null, "", null));

        assertThat(updated.template()).isNull();
    }

    @Test
    @DisplayName("종료일시만 보내도 기존 시작일시와 비교해 EVENT400-0 을 던진다")
    void 수정_후_기간이_역전되면_400을_던진다() {
        EventUpdateRequest request = new EventUpdateRequest(
                null, null, OffsetDateTime.parse("2026-06-30T00:00:00+09:00"), null, null);

        assertThatThrownBy(() -> eventService.update(2L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.INVALID_PERIOD.getCode());
        assertThat(eventService.findAdminEvent(2L).endAt())
                .isEqualTo(OffsetDateTime.parse("2026-07-31T23:59:59+09:00"));
    }

    @Test
    @DisplayName("종료(ENDED)된 이벤트는 수정할 수 없고 EVENT409-0 을 던진다")
    void 종료된_이벤트는_수정할_수_없다() {
        EventUpdateRequest request = new EventUpdateRequest("이름 변경", null, null, null, null);

        assertThatThrownBy(() -> eventService.update(6L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_ENDED_NOT_EDITABLE.getCode());
        assertThat(eventService.findAdminEvent(6L).name()).isEqualTo("여름 데이터 대방출");
    }

    @Test
    @DisplayName("없거나 삭제된 이벤트 수정은 EVENT404-0 이다")
    void 없는_이벤트_수정은_404를_던진다() {
        EventUpdateRequest request = new EventUpdateRequest("이름 변경", null, null, null, null);

        assertThatThrownBy(() -> eventService.update(999L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
        assertThatThrownBy(() -> eventService.update(99L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.EVENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("없는 템플릿 키로 수정하면 EVENT404-1 이다")
    void 없는_템플릿으로_수정하면_404를_던진다() {
        EventUpdateRequest request = new EventUpdateRequest(null, null, null, "없는키", null);

        assertThatThrownBy(() -> eventService.update(2L, request))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo(EventErrorCode.TEMPLATE_NOT_FOUND.getCode());
    }
}
