package com.newvent.event.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.newvent.event.dto.response.EventVersionListResponse;
import com.newvent.event.service.EventVersionService;

@ExtendWith(MockitoExtension.class)
class EventVersionControllerTest {

    @Mock
    private EventVersionService eventVersionService;
    @InjectMocks
    private EventVersionController eventVersionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(eventVersionController).build();
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
}
