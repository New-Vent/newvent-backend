package com.newvent.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.newvent.auth.cookie.RefreshCookies;
import com.newvent.auth.dto.TokenResponse;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.auth.service.AuthService;
import com.newvent.auth.service.RefreshTokenService;
import com.newvent.auth.web.AccountType;
import com.newvent.common.config.SecurityConfig;

/**
 * 인증 경로 · 쿠키 · 인가 규칙 · CSRF 검사가 서로 맞물리는지 본다. **DB 없이 돈다.**
 *
 * ★ AuthFlowTest(실제 DB)와 나눈 이유
 *   경로를 옮기면 컨트롤러 매핑 · SecurityConfig 공개 경로 · CsrfOriginFilter 보호 경로가 같이 바뀌어야 한다.
 *   셋 중 하나라도 어긋나면 로그인이 401 이 되거나 CSRF 검사가 조용히 꺼진다.
 *   이건 DB 없이도 잡을 수 있고, 로컬에서 바로 돌아야 한다.
 *
 * ★ dev 값을 여기서 고정한다 — CI 환경변수와 무관하게 돌아야 한다.
 *   CI 는 develop 대상에서도 CORS_ALLOWED_ORIGINS(배포 도메인) · AUTH_COOKIE_SECURE 를 secrets 로 넣고,
 *   환경변수는 설정 파일보다 우선한다. 고정하지 않으면 5174 Origin · Secure 없음 검사가 CI 에서만 깨진다.
 *   "환경변수 없이 뜨면 이 값이 나오는가" 는 AuthPropsTest 가 환경변수를 뺀 채 설정 파일로 본다.
 *   배포 설정은 AuthControllerProdTest 가 본다.
 */
@WebMvcTest({UserAuthController.class, AdminAuthController.class})
@Import({SecurityConfig.class, JwtProvider.class, RefreshCookies.class})
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "auth.cookie.secure=false",
        "auth.cookie.same-site=Lax",
        "auth.cors.allowed-origins=http://localhost:5173,http://localhost:5174"
})
class AuthControllerTest {

    private static final String ORIGIN = "http://localhost:5174";
    private static final String BODY = "{\"loginId\":\"admin\",\"password\":\"pw\"}";

    @Autowired MockMvc mvc;

    @MockitoBean AuthService authService;
    @MockitoBean RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        when(refreshTokenService.ttl()).thenReturn(Duration.ofDays(7));
        var issued = new AuthService.Issued(
                new TokenResponse("access-token", Instant.now().plusSeconds(1800), 1800), "raw-refresh");
        when(authService.loginUser(anyString(), anyString(), any())).thenReturn(issued);
        when(authService.loginAdmin(anyString(), anyString(), any())).thenReturn(issued);
        when(authService.refresh(any(), any())).thenReturn(issued);
    }

    @Test
    @DisplayName("관리자 로그인은 /api/admin/auth/login — 토큰 없이 통과하고 nv_admin_rt(Path=/api/admin/auth) 를 준다")
    void 관리자_로그인() throws Exception {
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(cookie().value("nv_admin_rt", "raw-refresh"))
                .andExpect(cookie().path("nv_admin_rt", "/api/admin/auth"))
                .andExpect(cookie().httpOnly("nv_admin_rt", true))
                .andExpect(cookie().secure("nv_admin_rt", false))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(cookie().doesNotExist("nv_user_rt"));
        verify(authService).loginAdmin("admin", "pw", null);
    }

    @Test
    @DisplayName("사용자 로그인은 /api/auth/login — nv_user_rt(Path=/api/auth) 를 준다")
    void 사용자_로그인() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(cookie().path("nv_user_rt", "/api/auth"))
                .andExpect(cookie().doesNotExist("nv_admin_rt"));
        verify(authService).loginUser("admin", "pw", null);
    }

    @Test
    @DisplayName("재로그인하면 지금 들고 있는 같은 계정 종류의 쿠키만 서비스에 넘긴다 (옛 토큰 폐기용)")
    void 재로그인은_같은_종류의_쿠키만_넘긴다() throws Exception {
        var both = new Cookie[] {new Cookie("nv_admin_rt", "old-admin"), new Cookie("nv_user_rt", "old-user")};

        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY).cookie(both))
                .andExpect(status().isOk());
        verify(authService).loginAdmin("admin", "pw", "old-admin");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY).cookie(both))
                .andExpect(status().isOk());
        verify(authService).loginUser("admin", "pw", "old-user");
    }

    @Test
    @DisplayName("관리자 refresh 는 관리자 쿠키만 읽어 관리자 대상으로 재발급한다")
    void 관리자_갱신() throws Exception {
        mvc.perform(post("/api/admin/auth/refresh").header("Origin", ORIGIN)
                        .cookie(new Cookie("nv_admin_rt", "admin-raw"), new Cookie("nv_user_rt", "user-raw")))
                .andExpect(status().isOk())
                .andExpect(cookie().path("nv_admin_rt", "/api/admin/auth"));
        verify(authService).refresh(AccountType.ADMIN, "admin-raw");
    }

    @Test
    @DisplayName("관리자 refresh 에 사용자 쿠키만 오면 쿠키가 없는 것으로 본다")
    void 관리자_갱신에_사용자_쿠키() throws Exception {
        mvc.perform(post("/api/admin/auth/refresh").header("Origin", ORIGIN)
                .cookie(new Cookie("nv_user_rt", "user-raw")));
        verify(authService).refresh(eq(AccountType.ADMIN), isNull());
    }

    @Test
    @DisplayName("관리자 logout 은 204 + nv_admin_rt 만료, 사용자 쿠키는 건드리지 않는다")
    void 관리자_로그아웃() throws Exception {
        mvc.perform(post("/api/admin/auth/logout").header("Origin", ORIGIN)
                        .cookie(new Cookie("nv_admin_rt", "admin-raw")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("nv_admin_rt", 0))
                .andExpect(cookie().path("nv_admin_rt", "/api/admin/auth"))
                .andExpect(cookie().doesNotExist("nv_user_rt"));
        verify(authService).logout(AccountType.ADMIN, "admin-raw");
    }

    @Test
    @DisplayName("★ refresh · logout 네 경로 모두 허용 Origin 이 없으면 403 이고 서비스까지 안 간다")
    void csrf_네_경로() throws Exception {
        for (String path : AccountType.cookieOnlyPaths()) {
            mvc.perform(post(path).cookie(new Cookie("nv_admin_rt", "x"), new Cookie("nv_user_rt", "x")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH403-0"));
        }
        verify(authService, never()).refresh(any(), any());
        verify(authService, never()).logout(any(), any());
    }

    @Test
    @DisplayName("login 은 CSRF 검사 대상이 아니다 — Origin 없이도 200")
    void 로그인은_csrf_대상이_아니다() throws Exception {
        for (AccountType a : AccountType.values()) {
            mvc.perform(post(a.login()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("dev: Vite 사용자(5173) · 관리자(5174) 개발 서버 Origin 은 refresh 를 통과한다")
    void dev_개발서버_origin() throws Exception {
        for (String origin : new String[] {"http://localhost:5173", "http://localhost:5174"}) {
            mvc.perform(post("/api/auth/refresh").header("Origin", origin)
                            .cookie(new Cookie("nv_user_rt", "user-raw")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("옛 경로(/auth/…)는 더 이상 로그인 엔드포인트가 아니다")
    void 옛_경로는_없다() throws Exception {
        mvc.perform(post("/auth/admin/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).loginAdmin(anyString(), anyString(), any());
    }
}
