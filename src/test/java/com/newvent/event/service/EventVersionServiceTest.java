package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
import com.newvent.event.dto.response.EventVersionDetailResponse;
import com.newvent.event.dto.response.EventVersionListResponse;
import com.newvent.event.dto.response.EventVersionSummaryResponse;
import com.newvent.event.exception.EventException;
import com.newvent.event.repository.EventRepository;
import com.newvent.event.repository.EventVersionRepository;
import com.newvent.generation.domain.ChatMessage;

@ExtendWith(MockitoExtension.class)
public class EventVersionServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock private EventVersionRepository eventVersionRepository;

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-28T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private EventVersionService eventVersionService;

    @BeforeEach
    void setUp() {
        eventVersionService = new EventVersionService(
                eventRepository, eventVersionRepository, FIXED_CLOCK);
    }

    @Test
    void getVersions_returnsCheckpointSummariesWithPublishedAndSourceInformation() {
        Long eventId = 12L;
        Event event = org.mockito.Mockito.mock(Event.class);
        EventVersion version = org.mockito.Mockito.mock(EventVersion.class);
        EventVersion sourceVersion = org.mockito.Mockito.mock(EventVersion.class);
        ChatMessage requestMessage = org.mockito.Mockito.mock(ChatMessage.class);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-09-21T14:20:00+09:00");

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByEventIdAndCheckpointTrueOrderByVersionNoDesc(eventId))
                .thenReturn(List.of(version));
        when(event.getTitle()).thenReturn("2026 월드컵 응원 이벤트");
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getPublishedVersion()).thenReturn(version);
        when(version.getId()).thenReturn(102L);
        when(version.getVersionNo()).thenReturn(2);
        when(version.getCreatedAt()).thenReturn(createdAt);
        when(version.getSourceVersion()).thenReturn(sourceVersion);
        when(sourceVersion.getVersionNo()).thenReturn(1);
        when(version.getRequestMessage()).thenReturn(requestMessage);
        when(requestMessage.getContent()).thenReturn("소개 문구를 친근하게 정리해 줘");

        EventVersionListResponse response = eventVersionService.getVersions(eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.title()).isEqualTo("2026 월드컵 응원 이벤트");
        assertThat(response.versions()).hasSize(1);
        EventVersionSummaryResponse summary = response.versions().getFirst();
        assertThat(summary.versionId()).isEqualTo(102L);
        assertThat(summary.versionNo()).isEqualTo(2);
        assertThat(summary.createdAt()).isEqualTo(createdAt);
        assertThat(summary.published()).isTrue();
        assertThat(summary.sourceVersionNo()).isEqualTo(1);
        assertThat(summary.requestContent()).isEqualTo("소개 문구를 친근하게 정리해 줘");
        verify(eventVersionRepository).findByEventIdAndCheckpointTrueOrderByVersionNoDesc(eventId);
    }

    @Test
    void getVersions_returnsEmptyListWhenNoCheckpointsExist() {
        Long eventId = 12L;
        Event event = org.mockito.Mockito.mock(Event.class);
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.of(event));
        when(eventVersionRepository.findByEventIdAndCheckpointTrueOrderByVersionNoDesc(eventId))
                .thenReturn(List.of());
        when(event.getTitle()).thenReturn("2026 월드컵 응원 이벤트");

        EventVersionListResponse response = eventVersionService.getVersions(eventId);

        assertThat(response.versions()).isEmpty();
    }

    @Test
    void unmarkCheckpoint_rejectsCurrentlyPublishedVersion() {
        Long eventId = 12L;
        Long versionId = 102L;
        Event event = mock(Event.class);
        EventVersion version = mock(EventVersion.class);

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(versionId, eventId))
                .thenReturn(Optional.of(version));
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getPublishedVersion()).thenReturn(version);
        when(version.getId()).thenReturn(versionId);

        assertThatThrownBy(() -> eventVersionService.unmarkCheckpoint(eventId, versionId))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo("EVENT409-0");

        verify(version, never()).unmarkCheckpoint();
    }

    @Test
    void unmarkCheckpoint_allowsOtherVersionWhileEventIsPublished() {
        Long eventId = 12L;
        Long versionId = 102L;
        Event event = mock(Event.class);
        EventVersion version = mock(EventVersion.class);
        EventVersion publishedVersion = mock(EventVersion.class);

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(versionId, eventId))
                .thenReturn(Optional.of(version));
        when(event.getStatus()).thenReturn(EventStatus.PUBLISHED);
        when(event.getPublishedVersion()).thenReturn(publishedVersion);
        when(publishedVersion.getId()).thenReturn(103L);

        eventVersionService.unmarkCheckpoint(eventId, versionId);

        verify(version).unmarkCheckpoint();
    }

    @Test
    void getVersions_throwsWhenEventDoesNotExistOrIsDeleted() {
        Long eventId = 12L;
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventVersionService.getVersions(eventId))
                .isInstanceOf(EventException.class);
        verifyNoInteractions(eventVersionRepository);
    }

    @Test
    void markCheckpoint_marksVersionBelongingToEvent() {
        Long eventId = 12L;
        Long versionId = 102L;
        Event event = mock(Event.class);
        EventVersion version = mock(EventVersion.class);

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(versionId, eventId))
                .thenReturn(Optional.of(version));

        eventVersionService.markCheckpoint(eventId, versionId);

        verify(version).markCheckpoint(OffsetDateTime.now(FIXED_CLOCK));
        verify(eventVersionRepository).findByIdAndEventId(versionId, eventId);
    }

    @Test
    void unmarkCheckpoint_unmarksVersionBelongingToEvent() {
        Long eventId = 12L;
        Long versionId = 102L;
        Event event = mock(Event.class);
        EventVersion version = mock(EventVersion.class);

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventId(versionId, eventId))
                .thenReturn(Optional.of(version));

        eventVersionService.unmarkCheckpoint(eventId, versionId);

        verify(version).unmarkCheckpoint();
        verify(eventVersionRepository).findByIdAndEventId(versionId, eventId);
    }

    @Test
    void markCheckpoint_rejectsMissingOrDeletedEvent() {
        Long eventId = 12L;
        Long versionId = 102L;
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventVersionService.markCheckpoint(eventId, versionId))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo("EVENT404-0");

        verifyNoInteractions(eventVersionRepository);
    }

    @Test
    void markCheckpoint_rejectsVersionFromAnotherEvent() {
        Long eventId = 12L;
        Long versionId = 102L;
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(mock(Event.class)));

        // 이 eventId에 속하는 versionId가 없으면 빈 값
        when(eventVersionRepository.findByIdAndEventId(versionId, eventId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventVersionService.markCheckpoint(eventId, versionId))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo("EVENT404-3");
    }

    @Test
    void getVersion_returnsCheckpointHtml() {
        Long eventId = 12L;
        Long versionId = 102L;
        OffsetDateTime createdAt =
                OffsetDateTime.parse("2026-09-21T14:20:00+09:00");

        Event event = mock(Event.class);
        EventVersion version = mock(EventVersion.class);

        when(eventRepository.findByIdAndDeletedAtIsNull(eventId))
                .thenReturn(Optional.of(event));
        when(eventVersionRepository.findByIdAndEventIdAndCheckpointTrue(versionId, eventId))
                .thenReturn(Optional.of(version));
        when(version.getId()).thenReturn(versionId);
        when(version.getVersionNo()).thenReturn(2);
        when(version.getCreatedAt()).thenReturn(createdAt);
        when(version.getHtmlContent())
                .thenReturn("<html><body>저장된 화면</body></html>");

        EventVersionDetailResponse response =
                eventVersionService.getVersion(eventId, versionId);

        assertThat(response.versionId()).isEqualTo(versionId);
        assertThat(response.versionNo()).isEqualTo(2);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.htmlContent())
                .isEqualTo("<html><body>저장된 화면</body></html>");

        verify(eventVersionRepository)
                .findByIdAndEventIdAndCheckpointTrue(versionId, eventId);
    }

    @Test
    void getVersion_throwsWhenEventIsMissingOrDeleted() {
        when(eventRepository.findByIdAndDeletedAtIsNull(12L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventVersionService.getVersion(12L, 102L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo("EVENT404-0");

        verifyNoInteractions(eventVersionRepository);
    }

    @Test
    void getVersion_throwsWhenVersionIsNotACheckpointForEvent() {
        when(eventRepository.findByIdAndDeletedAtIsNull(12L))
                .thenReturn(Optional.of(mock(Event.class)));
        when(eventVersionRepository.findByIdAndEventIdAndCheckpointTrue(102L, 12L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventVersionService.getVersion(12L, 102L))
                .isInstanceOf(EventException.class)
                .extracting(ex -> ((EventException) ex).getErrorCode().getCode())
                .isEqualTo("EVENT404-3");
    }
}
