package com.newvent.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.newvent.common.config.SecurityConfig;

/**
 * prod 프로파일로 떴을 때의 인증 동작. **DB 없이 돈다.**
 *
 * ★ 배포에서 넣는 환경변수(JWT_SECRET · CORS_ALLOWED_ORIGINS)를 준다.
 *   Secure · SameSite 는 auth.cookie.* 로 고정한다 — CI 의 AUTH_COOKIE_* secrets 가 설정 파일보다 우선해서,
 *   고정하지 않으면 secrets 값에 따라 결과가 달라진다. prod 설정 파일의 값 자체는 AuthPropsTest 가 본다.
 *
 * ★ prod 에서 Secure 를 끄면 ProdAuthGuard 가 기동을 중단한다.
 *   설정 누락·덮어쓰기의 기동 실패는 ProdAuthGuardTest 에서 실제 설정 파일로 검증한다.
 *
 * ★ 경로 · 쿠키 이름은 dev 와 같아야 한다 — 로컬에서 통과한 게 배포에서도 통과하도록.
 */
@WebMvcTest({UserAuthController.class, AdminAuthController.class})
@Import({SecurityConfig.class, JwtProvider.class, RefreshCookies.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "JWT_SECRET=Xk9f3Q7v2Lm8Zp1Rt6Yw4Nc0Bh5Jd8Se2Ug7Ka3",
        "CORS_ALLOWED_ORIGINS=https://newvent.duckdns.org",
        "auth.cookie.secure=true",
        "auth.cookie.same-site=Lax"
})
class AuthControllerProdTest {

    private static final String PROD_ORIGIN = "https://newvent.duckdns.org";
    private static final String BODY = "{\"loginId\":\"admin\",\"password\":\"pw\"}";

    @Autowired MockMvc mvc;

    @MockitoBean AuthService authService;
    @MockitoBean RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        when(refreshTokenService.ttl()).thenReturn(Duration.ofDays(7));
        var issued = new AuthService.Issued(
                new TokenResponse("access-token", Instant.now().plusSeconds(1800), 1800), "raw-refresh");
        when(authService.loginAdmin(anyString(), anyString(), any())).thenReturn(issued);
        when(authService.refresh(any(), any())).thenReturn(issued);
    }

    @Test
    @DisplayName("prod: 쿠키는 Secure 다 — 경로·이름은 dev 와 같다")
    void secure_쿠키() throws Exception {
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(cookie().secure("nv_admin_rt", true))
                .andExpect(cookie().path("nv_admin_rt", "/api/admin/auth"))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @Test
    @DisplayName("prod: 서비스 주소 Origin 은 refresh 를 통과한다")
    void 서비스_origin_통과() throws Exception {
        mvc.perform(post("/api/admin/auth/refresh").header("Origin", PROD_ORIGIN)
                        .cookie(new Cookie("nv_admin_rt", "admin-raw")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("prod: 로컬 개발 서버 Origin 은 거부한다 — dev 허용 목록이 섞이지 않는다")
    void 로컬_origin_거부() throws Exception {
        for (String origin : new String[] {"http://localhost:5173", "http://localhost:5174"}) {
            mvc.perform(post("/api/admin/auth/refresh").header("Origin", origin)
                            .cookie(new Cookie("nv_admin_rt", "admin-raw")))
                    .andExpect(status().isForbidden());
        }
        // Referer 로 우회해도 마찬가지다 (Origin 없는 요청)
        mvc.perform(post("/api/admin/auth/logout").header("Referer", "http://localhost:5174/admin/")
                        .cookie(new Cookie("nv_admin_rt", "admin-raw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH403-0"));
    }
}
