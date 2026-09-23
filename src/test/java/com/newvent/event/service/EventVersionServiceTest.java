package com.newvent.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.newvent.event.domain.Event;
import com.newvent.event.domain.EventStatus;
import com.newvent.event.domain.EventVersion;
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
    @InjectMocks
    private EventVersionService eventVersionService;

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
    void getVersions_throwsWhenEventDoesNotExistOrIsDeleted() {
        Long eventId = 12L;
        when(eventRepository.findByIdAndDeletedAtIsNull(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventVersionService.getVersions(eventId))
                .isInstanceOf(EventException.class);
        verifyNoInteractions(eventVersionRepository);
    }
}
