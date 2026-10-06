package com.newvent.event.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.common.config.SecurityConfig;
import com.newvent.common.exception.handler.GlobalExceptionHandler;
import com.newvent.event.dto.response.*;
import com.newvent.event.service.TemplateLibraryService;

@WebMvcTest(TemplateLibraryController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtProvider.class})
class TemplateLibraryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TemplateLibraryService library;
    RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(AuthUser.admin(1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void listUsesAuthenticatedAdministrator() throws Exception {
        when(library.list(1L, null, null, false, 0, 12)).thenReturn(
                PageResponse.of(List.of(new TemplateLibraryResponse("custom_test", "샘플", null, false, true, null)),
                        0, 12, 1));
        mvc.perform(get("/api/admin/template-library").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].templateKey").value("custom_test"));
    }

    @Test
    void anonymousAndRegularUsersCannotReadLibrary() throws Exception {
        mvc.perform(get("/api/admin/template-library")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/template-library").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(library);
    }

    @Test
    void registrationAcceptsSourceIdentifiersWithoutHtml() throws Exception {
        when(library.register(eq(1L), any())).thenReturn(
                new TemplateLibraryResponse("custom_test", "내 템플릿", null, false, true, null));
        mvc.perform(post("/api/admin/template-library").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":2,"sourceVersionId":7,"name":"내 템플릿"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.templateKey").value("custom_test"));
        verify(library).register(eq(1L), argThat(r -> r.eventId().equals(2L) && r.sourceVersionId().equals(7L)));
    }

    @Test
    void missingSourceVersionIsRejected() throws Exception {
        mvc.perform(post("/api/admin/template-library").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":2,\"name\":\"템플릿\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(library);
    }

    @Test
    void previewAndMetadataAndDeactivationHaveSeparateRoutes() throws Exception {
        when(library.preview(1L, "custom_test")).thenReturn(new TemplatePreviewResponse("custom_test", "<div>미리보기</div>"));
        when(library.update(eq(1L), eq("custom_test"), any())).thenReturn(
                new TemplateLibraryResponse("custom_test", "변경", null, false, true, null));
        mvc.perform(get("/api/admin/template-library/custom_test/preview").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.html").value("<div>미리보기</div>"));
        mvc.perform(patch("/api/admin/template-library/custom_test").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"변경\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/template-library/custom_test").with(admin())).andExpect(status().isOk());
        verify(library).deactivate(1L, "custom_test");
    }

    @Test
    void useReturnsBothNewEventAndInitialVersion() throws Exception {
        when(library.use(eq(1L), eq("custom_test"), any())).thenReturn(new TemplateUseResponse(10L, 11L, 1));
        mvc.perform(post("/api/admin/template-library/custom_test/events").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"새 이벤트","startAt":"2026-10-10T00:00:00+09:00",
                                 "endAt":"2026-10-20T00:00:00+09:00"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.eventId").value(10))
                .andExpect(jsonPath("$.data.versionId").value(11));
    }

    @Test
    void invalidPageSizeIsRejected() throws Exception {
        mvc.perform(get("/api/admin/template-library?size=51").with(admin())).andExpect(status().isBadRequest());
        verifyNoInteractions(library);
    }
}
