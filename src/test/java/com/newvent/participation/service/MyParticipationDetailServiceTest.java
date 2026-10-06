package com.newvent.participation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.newvent.event.domain.Event;
import com.newvent.participation.domain.EventParticipation;
import com.newvent.participation.dto.response.MyParticipationDetailResponse;
import com.newvent.participation.exception.ParticipationErrorCode;
import com.newvent.participation.exception.ParticipationException;
import com.newvent.participation.repository.EventParticipationRepository;

class MyParticipationDetailServiceTest {

    private final EventParticipationRepository repository = mock(EventParticipationRepository.class);

    private final MyParticipationDetailService service = new MyParticipationDetailService(repository);

    @Test
    void 본인의_참여_정보와_제출값과_결과를_반환한다() {
        Map<String, Object> submittedData = Map.of("pouchIndex", 2);
        Map<String, Object> resultData = Map.of(
                "status", "WON",
                "prizeName", "커피 쿠폰"
        );

        EventParticipation participation = participation(submittedData, resultData);

        when(repository.findByIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(participation));

        MyParticipationDetailResponse response = service.getParticipation(7L, 30L);

        assertThat(response.participationId()).isEqualTo(30L);
        assertThat(response.eventId()).isEqualTo(10L);
        assertThat(response.eventTitle()).isEqualTo("복주머니 이벤트");
        assertThat(response.participatedAt()).isEqualTo(OffsetDateTime.parse("2026-10-02T11:00:00+09:00"));
        assertThat(response.submittedData()).isEqualTo(submittedData);
        assertThat(response.resultData()).isEqualTo(resultData);

        verify(repository).findByIdAndUserId(30L, 7L);
    }

    @Test
    void 결과_대기_데이터를_그대로_반환한다() {
        Map<String, Object> submittedData = Map.of("prediction", "HOME_WIN");
        Map<String, Object> resultData = Map.of("status", "PENDING");

        EventParticipation participation = participation(submittedData, resultData);

        when(repository.findByIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(participation));

        MyParticipationDetailResponse response = service.getParticipation(7L, 30L);

        assertThat(response.submittedData()).isEqualTo(submittedData);
        assertThat(response.resultData()).isEqualTo(resultData);
    }

    @Test
    void 미당첨_결과를_그대로_반환한다() {
        Map<String, Object> submittedData = Map.of("pouchIndex", 2);
        Map<String, Object> resultData = Map.of("status", "LOST");

        EventParticipation participation = participation(submittedData, resultData);

        when(repository.findByIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(participation));

        MyParticipationDetailResponse response = service.getParticipation(7L, 30L);

        assertThat(response.submittedData()).isEqualTo(submittedData);
        assertThat(response.resultData()).isEqualTo(resultData);
    }

    @Test
    void 단순_참여의_빈_데이터를_그대로_반환한다() {
        EventParticipation participation = participation(Map.of(), Map.of());

        when(repository.findByIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(participation));

        MyParticipationDetailResponse response = service.getParticipation(7L, 30L);

        assertThat(response.submittedData()).isEmpty();
        assertThat(response.resultData()).isEmpty();
    }

    @Test
    void 기록이_없으면_참여_기록_없음_예외를_던진다() {
        when(repository.findByIdAndUserId(999L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getParticipation(7L, 999L))
                .isInstanceOfSatisfying(
                        ParticipationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_FOUND)
                );

        verify(repository).findByIdAndUserId(999L, 7L);
    }

    @Test
    void 다른_사용자의_기록도_참여_기록_없음_예외로_처리한다() {
        when(repository.findByIdAndUserId(30L, 8L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getParticipation(8L, 30L))
                .isInstanceOfSatisfying(
                        ParticipationException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ParticipationErrorCode.PARTICIPATION_NOT_FOUND)
                );

        verify(repository).findByIdAndUserId(30L, 8L);
    }

    @Test
    void 응답의_Map은_엔티티의_Map과_별도_객체다() {
        Map<String, Object> submittedData = new LinkedHashMap<>();
        submittedData.put("pouchIndex", 2);

        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("status", "WON");
        resultData.put("prizeName", "커피 쿠폰");

        EventParticipation participation = participation(submittedData, resultData);

        when(repository.findByIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(participation));

        MyParticipationDetailResponse response = service.getParticipation(7L, 30L);

        assertThat(response.submittedData()).isNotSameAs(submittedData);
        assertThat(response.resultData()).isNotSameAs(resultData);

        response.submittedData().put("pouchIndex", 3);
        response.resultData().put("status", "LOST");

        assertThat(submittedData.get("pouchIndex")).isEqualTo(2);
        assertThat(resultData.get("status")).isEqualTo("WON");
    }

    private EventParticipation participation(
            Map<String, Object> submittedData,
            Map<String, Object> resultData
    ) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(10L);
        when(event.getTitle()).thenReturn("복주머니 이벤트");

        EventParticipation participation = mock(EventParticipation.class);

        when(participation.getId()).thenReturn(30L);
        when(participation.getEvent()).thenReturn(event);
        when(participation.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-10-02T11:00:00+09:00"));
        when(participation.getSubmittedData()).thenReturn(submittedData);
        when(participation.getResultData()).thenReturn(resultData);

        return participation;
    }
}
