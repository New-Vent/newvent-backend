package com.newvent.event.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.newvent.event.dto.response.DirectEditResponse;
import com.newvent.event.dto.response.EventVersionDetailResponse;
import com.newvent.event.dto.response.EventVersionListResponse;
import com.newvent.event.service.DirectEditService;
import com.newvent.event.service.EventVersionService;

@ExtendWith(MockitoExtension.class)
class EventVersionControllerTest {

    @Mock
    private EventVersionService eventVersionService;
    @Mock
    private DirectEditService directEditService;
    @InjectMocks
    private EventVersionController eventVersionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(eventVersionController)
                .setControllerAdvice(new com.newvent.common.exception.handler.GlobalExceptionHandler())
                .build();
    }

    @Test
    void getVersions_returnsCheckpointListThroughAdminEndpoint() throws Exception {
        Long eventId = 12L;
        EventVersionListResponse response = EventVersionListResponse.builder()
                .eventId(eventId)
                .title("2026 월드컵 응원 이벤트")
                .versions(List.of())
                .build();
        when(eventVersionService.getVersions(eventId)).thenReturn(response);

        mockMvc.perform(get("/api/admin/events/{eventId}/versions", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventId").value(12))
                .andExpect(jsonPath("$.data.title").value("2026 월드컵 응원 이벤트"))
                .andExpect(jsonPath("$.data.versions").isArray())
                .andExpect(jsonPath("$.data.versions").isEmpty());

        verify(eventVersionService).getVersions(eventId);
    }

    @Test
    void markCheckpoint_returnsSuccess() throws Exception {
        mockMvc.perform(put(
                        "/api/admin/events/{eventId}/versions/{versionId}/checkpoint",
                        12L, 102L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventVersionService).markCheckpoint(12L, 102L);
    }

    @Test
    void unmarkCheckpoint_returnsSuccess() throws Exception {
        mockMvc.perform(delete(
                        "/api/admin/events/{eventId}/versions/{versionId}/checkpoint",
                        12L, 102L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventVersionService).unmarkCheckpoint(12L, 102L);
    }

    @Test
    void getVersion_returnsSelectedVersionHtml() throws Exception {
        EventVersionDetailResponse response = EventVersionDetailResponse.builder()
                .versionId(102L)
                .versionNo(2)
                .createdAt(OffsetDateTime.parse("2026-09-21T14:20:00+09:00"))
                .htmlContent("<html><body>저장된 화면</body></html>")
                .build();

        when(eventVersionService.getVersion(12L, 102L)).thenReturn(response);

        mockMvc.perform(get(
                        "/api/admin/events/{eventId}/versions/{versionId}",
                        12L, 102L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionId").value(102))
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-09-21T14:20:00+09:00"))
                .andExpect(jsonPath("$.data.htmlContent")
                        .value("<html><body>저장된 화면</body></html>"));

        verify(eventVersionService).getVersion(12L, 102L);
    }

    @Test
    void directEdit_returnsSuccessWithNewVersion() throws Exception {
        Long eventId = 12L;
        DirectEditResponse response = new DirectEditResponse(103L, 3);
        when(directEditService.directEdit(org.mockito.ArgumentMatchers.eq(eventId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        String requestJson = """
                {
                    "sourceVersionId": 102,
                    "edits": [
                        {
                            "index": 0,
                            "before": "이전 문구",
                            "after": "새 문구"
                        }
                    ],
                    "buttonStyle": {
                        "background": "#d60076",
                        "color": "#ffffff",
                        "size": "medium",
                        "shape": "pill"
                    }
                }
                """;

        mockMvc.perform(post("/api/admin/events/{eventId}/versions/direct-edit", eventId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionId").value(103))
                .andExpect(jsonPath("$.data.versionNo").value(3));

        verify(directEditService).directEdit(org.mockito.ArgumentMatchers.eq(eventId), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void directEdit_whenBeforeTextMismatch_returnsConflict409() throws Exception {
        Long eventId = 12L;
        when(directEditService.directEdit(org.mockito.ArgumentMatchers.eq(eventId), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.newvent.event.exception.DirectEditException(
                        com.newvent.event.exception.DirectEditErrorCode.BEFORE_TEXT_MISMATCH));

        String requestJson = """
                {
                    "sourceVersionId": 102,
                    "edits": [
                        {
                            "index": 0,
                            "before": "불일치 문구",
                            "after": "새 문구"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/admin/events/{eventId}/versions/direct-edit", eventId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(com.newvent.event.exception.DirectEditErrorCode.BEFORE_TEXT_MISMATCH.getCode()))
                .andExpect(jsonPath("$.message").value(com.newvent.event.exception.DirectEditErrorCode.BEFORE_TEXT_MISMATCH.getMessage()));
    }

    @Test
    void directEdit_whenMissingSourceVersionId_returnsBadRequest400() throws Exception {
        Long eventId = 12L;

        String requestJson = """
                {
                    "edits": [
                        {
                            "index": 0,
                            "before": "이전 문구",
                            "after": "새 문구"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/admin/events/{eventId}/versions/direct-edit", eventId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }
}
