package com.newvent.auth;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.newvent.auth.config.AuthProps;
import com.newvent.auth.dto.AuthUser;
import com.newvent.auth.entity.RefreshToken;
import com.newvent.auth.repository.RefreshTokenRepository;
import com.newvent.auth.service.RefreshTokenService;
import com.newvent.auth.web.AccountType;
import com.newvent.user.domain.MembershipGrade;
import com.newvent.user.domain.User;
import com.newvent.user.repository.UserRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 로그인(사용자/관리자) → 갱신(Rotation) → 로그아웃, 그리고 경로별 인가 규칙.
 *
 * UserRepositoryTest 와 같이 실제 datasource(docker-compose PostgreSQL)를 쓴다.
 * 요청이 커밋되므로 롤백되지 않는다 — login_id 가 authtest_ 로 시작하는 계정만 만들고 지운다
 * (refresh_tokens 는 ON DELETE CASCADE 로 함께 지워진다). 더미 데이터(V3)는 건드리지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    private static final String PASSWORD = "pw-1234";

    // ★ 경로·쿠키 이름은 AccountType 에서 받는다 — 테스트가 옛 문자열을 들고 있으면 바뀐 걸 못 잡는다
    private static final AccountType U = AccountType.USER;
    private static final AccountType A = AccountType.ADMIN;
    private static final String USER = "authtest_user";
    private static final String USER2 = "authtest_user2";
    private static final String ADMIN = "authtest_admin";
    private static final String INACTIVE_ADMIN = "authtest_admin_off";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired RefreshTokenService refreshTokenService;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Value("${auth.jwt.secret}") String jwtSecret;
    @Autowired AuthProps authProps;

    /**
     * 허용 Origin — refresh/logout 은 CsrfOriginFilter 때문에 필요하다.
     * 값을 박아 두지 않고 설정(CORS_ALLOWED_ORIGINS)에서 읽는다. CI 는 배포용 값으로 돌기 때문이다.
     */
    private String origin;

    @BeforeEach
    void setUp() {
        origin = authProps.cors().allowedOrigins().getFirst();
        cleanUp();
        users.save(new User(USER, encoder.encode(PASSWORD), "인증테스트", USER + "@test.com", null, 30000,
                MembershipGrade.BEST));   // 일부러 틀린 등급 — 로그인 시 재계산되는지 본다
        users.save(new User(USER2, encoder.encode(PASSWORD), "인증테스트2", USER2 + "@test.com", null, 30000,
                MembershipGrade.NORMAL));
        insertAdmin(ADMIN, true);
        insertAdmin(INACTIVE_ADMIN, false);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE login_id LIKE 'authtest\\_%'");
        jdbc.update("DELETE FROM admins WHERE login_id LIKE 'authtest\\_%'");
    }

    private void insertAdmin(String loginId, boolean active) {
        jdbc.update("INSERT INTO admins (login_id, password_hash, name, is_active) VALUES (?, ?, ?, ?)",
                loginId, encoder.encode(PASSWORD), "관리자", active);
    }

    private Long adminIdOf(String loginId) {
        return jdbc.queryForObject("SELECT id FROM admins WHERE login_id = ?", Long.class, loginId);
    }

    private Long userIdOf(String loginId) {
        return users.findByLoginId(loginId).orElseThrow().getId();
    }

    private MvcResult login(String path, String loginId, String password) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password)))
                .andReturn();
    }

    private Cookie userCookie(String loginId) throws Exception {
        return login(U.login(), loginId, PASSWORD).getResponse().getCookie(U.cookieName());
    }

    private String accessToken(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private Cookie adminCookie(String loginId) throws Exception {
        return login(A.login(), loginId, PASSWORD).getResponse().getCookie(A.cookieName());
    }

    /** 쿠키 이름으로 대상을 골라 그 대상의 refresh 로 보낸다 */
    private MvcResult refresh(Cookie cookie) throws Exception {
        AccountType accountType = A.cookieName().equals(cookie.getName()) ? A : U;
        return mvc.perform(post(accountType.refresh()).header("Origin", origin).cookie(cookie)).andReturn();
    }

    /** 응답에서 그 대상의 새 쿠키를 꺼낸다 */
    private static Cookie cookieOf(MvcResult r, AccountType accountType) {
        return r.getResponse().getCookie(accountType.cookieName());
    }

    private static String loginBody(String loginId, String password) {
        return "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password);
    }

    @Test
    @DisplayName("사용자 로그인: ApiResponse 로 Access Token, HttpOnly Refresh 쿠키(nv_user_rt, Path=/api/auth)가 내려온다")
    void 사용자_로그인_성공() throws Exception {
        mvc.perform(post(U.login()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + USER + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expireDate").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(cookie().httpOnly("nv_user_rt", true))
                .andExpect(cookie().path("nv_user_rt", "/api/auth"))
                .andExpect(cookie().doesNotExist("nv_admin_rt"))
                // Secure·SameSite 는 설정(AUTH_COOKIE_SECURE / AUTH_COOKIE_SAME_SITE)을 그대로 따른다
                .andExpect(cookie().secure("nv_user_rt", authProps.cookie().secure()))
                .andExpect(header().string("Set-Cookie",
                        containsString("SameSite=" + authProps.cookie().sameSite())));
    }

    @Test
    @DisplayName("사용자 로그인 시 멤버십 등급이 재계산돼 저장된다")
    void 로그인_등급_재계산() throws Exception {
        login(U.login(), USER, PASSWORD);

        // 요금제 30,000원(1점) + 가입 1년 미만(1점) = 2점 → NORMAL
        assertEquals(MembershipGrade.NORMAL, users.findByLoginId(USER).orElseThrow().getMembershipGrade());
    }

    @Test
    @DisplayName("틀린 비밀번호·없는 계정·다른 테이블의 계정은 모두 같은 401(AUTH401-0)")
    void 로그인_실패() throws Exception {
        MvcResult[] failures = {
                login(U.login(), USER, "wrong"),
                login(U.login(), "authtest_nobody", PASSWORD),
                login(U.login(), ADMIN, PASSWORD),              // 관리자 계정으로 사용자 로그인
                login(A.login(), USER, PASSWORD),         // 사용자 계정으로 관리자 로그인
                login(A.login(), INACTIVE_ADMIN, PASSWORD),   // 비활성 관리자
        };
        for (MvcResult r : failures) {
            assertEquals(401, r.getResponse().getStatus());
            assertNull(r.getResponse().getCookie(U.cookieName()));
            assertNull(r.getResponse().getCookie(A.cookieName()));
            org.hamcrest.MatcherAssert.assertThat(r.getResponse().getContentAsString(),
                    containsString("\"code\":\"AUTH401-0\""));
        }
    }

    @Test
    @DisplayName("관리자 로그인 → /api/admin/** 통과, 사용자는 403")
    void 관리자_로그인과_인가() throws Exception {
        MvcResult adminLogin = login(A.login(), ADMIN, PASSWORD);
        assertEquals(200, adminLogin.getResponse().getStatus());
        String admin = accessToken(adminLogin);
        String user = accessToken(login(U.login(), USER, PASSWORD));

        // 존재하지 않는 경로다 — 401/403 이 아니면 인가는 통과한 것
        assertPassesAuthorization("/api/admin/anything", admin);
        mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403-0"));
        assertPassesAuthorization("/api/anything", user);
        assertPassesAuthorization("/api/anything", admin);
    }

    private void assertPassesAuthorization(String path, String token) throws Exception {
        int status = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, status, path);
        assertNotEquals(403, status, path);
    }

    @Test
    @DisplayName("토큰 없음·위조·만료·claim 누락은 401(COMMON401-0) JSON, 회원가입은 토큰 없이 통과")
    void 인증_실패와_공개경로() throws Exception {
        var key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        var otherKey = Keys.hmacShaKeyFor("another-secret-another-secret-another-secret!!".getBytes(
                StandardCharsets.UTF_8));
        String expired = Jwts.builder().subject("1").claim("admin", true)
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(key).compact();
        String forged = Jwts.builder().subject("1").claim("admin", true)
                .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(otherKey).compact();
        String noAdminClaim = Jwts.builder().subject("1")
                .expiration(Date.from(Instant.now().plusSeconds(600))).signWith(key).compact();

        mvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(jsonPath("$.code").value("COMMON401-0"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        for (String token : new String[] {"garbage", expired, forged, noAdminClaim}) {
            mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON401-0"));
        }

        // 공개 경로 — 인증 없이 컨트롤러까지 가서 검증 실패(400)가 난다
        mvc.perform(post("/api/public/users/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refresh 는 새 토큰을 주고(Rotation), 이미 쓴 토큰을 다시 쓰면 401 + 쿠키 삭제")
    void 갱신_로테이션() throws Exception {
        Cookie first = userCookie(USER);

        MvcResult refreshed = mvc.perform(post(U.refresh()).header("Origin", origin).cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = cookieOf(refreshed, U);
        assertNotEquals(first.getValue(), second.getValue());
        assertEquals(200, refresh(second).getResponse().getStatus());       // 새 토큰은 동작

        mvc.perform(post(U.refresh()).header("Origin", origin).cookie(first))   // 재사용 → 거부 + 쿠키 삭제
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401-1"))
                .andExpect(cookie().maxAge(U.cookieName(), 0));
    }

    @Test
    @DisplayName("이미 회전된 토큰이 다시 오면 그 계정의 다른 세션까지 전부 폐기된다 (롤백되지 않는다)")
    void 재사용_탐지_전체_폐기() throws Exception {
        Cookie a = userCookie(USER);
        Cookie b = userCookie(USER);                                                  // 다른 기기
        Cookie other = userCookie(USER2);
        Cookie admin = adminCookie(ADMIN);

        Cookie a2 = cookieOf(refresh(a), U);
        assertEquals(401, refresh(a).getResponse().getStatus());                    // a 재사용 → 탈취 의심

        assertEquals(401, refresh(a2).getResponse().getStatus());
        assertEquals(401, refresh(b).getResponse().getStatus());
        assertEquals(200, refresh(other).getResponse().getStatus());                // 다른 사용자는 무관
        assertEquals(200, refresh(admin).getResponse().getStatus());                // 관리자 세션도 무관
    }

    @Test
    @DisplayName("관리자가 비활성화되면 가지고 있던 Refresh Token 으로도 재발급받지 못하고, 그 계정의 토큰이 전부 지워진다")
    void 비활성_관리자_갱신_거부() throws Exception {
        Cookie c = adminCookie(ADMIN);
        Cookie otherDevice = adminCookie(ADMIN);
        jdbc.update("UPDATE admins SET is_active = FALSE WHERE login_id = ?", ADMIN);

        mvc.perform(post(A.refresh()).header("Origin", origin).cookie(c))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401-1"));

        // 새 토큰을 발급하지 않고, 다른 기기의 토큰까지 그 계정의 토큰이 전부 지워진다 (롤백되지 않는다)
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE admin_id = ?", Integer.class, adminIdOf(ADMIN)));

        // 다시 활성화해도 비활성화 전 세션은 되살아나지 않는다
        jdbc.update("UPDATE admins SET is_active = TRUE WHERE login_id = ?", ADMIN);
        mvc.perform(post(A.refresh()).header("Origin", origin).cookie(otherDevice))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401-1"));
    }

    @Test
    @DisplayName("DB 에는 토큰 원문이 아니라 해시만, 주인은 user_id / admin_id 중 한쪽에만 저장된다")
    void 저장_형태() throws Exception {
        Cookie u = userCookie(USER);
        login(A.login(), ADMIN, PASSWORD);

        var userTokens = jdbc.queryForList(
                "SELECT token_hash, admin_id FROM refresh_tokens WHERE user_id = ?", userIdOf(USER));
        assertEquals(1, userTokens.size());
        assertNull(userTokens.get(0).get("admin_id"));
        assertNotEquals(u.getValue(), userTokens.get(0).get("token_hash"));
        assertEquals(64, ((String) userTokens.get(0).get("token_hash")).length());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE admin_id = ? AND user_id IS NULL",
                Integer.class, adminIdOf(ADMIN)));
    }

    @Test
    @DisplayName("쿠키 없이 refresh 하면 401")
    void 쿠키_없는_갱신() throws Exception {
        for (AccountType accountType : AccountType.values()) {
            mvc.perform(post(accountType.refresh()).header("Origin", origin))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401-1"));
        }
    }

    @Test
    @DisplayName("관리자 로그인 쿠키는 nv_admin_rt, Path=/api/admin/auth — 사용자 쿠키와 이름·경로가 다르다")
    void 관리자_쿠키() throws Exception {
        mvc.perform(post(A.login()).contentType(MediaType.APPLICATION_JSON).content(loginBody(ADMIN, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andExpect(cookie().httpOnly("nv_admin_rt", true))
                .andExpect(cookie().path("nv_admin_rt", "/api/admin/auth"))
                .andExpect(cookie().doesNotExist("nv_user_rt"));
    }

    @Test
    @DisplayName("같은 브라우저에서 사용자·관리자로 함께 로그인해도 서로의 세션을 건드리지 않는다")
    void 사용자_관리자_세션_공존() throws Exception {
        Cookie user = userCookie(USER);
        Cookie admin = adminCookie(ADMIN);

        MvcResult u = refresh(user);
        MvcResult a = refresh(admin);
        assertEquals(200, u.getResponse().getStatus());
        assertEquals(200, a.getResponse().getStatus());
        // 각자 자기 쿠키만 갱신한다
        assertNull(cookieOf(u, A));
        assertNull(cookieOf(a, U));
    }

    @Test
    @DisplayName("같은 경로에서 다른 계정으로 다시 로그인하면 옛 토큰은 DB 에서도 폐기된다 — 다른 계정 종류 쿠키는 그대로")
    void 재로그인_옛_토큰_폐기() throws Exception {
        Cookie user1 = userCookie(USER);
        Cookie admin = adminCookie(ADMIN);

        // 브라우저처럼 지금 쿠키를 실은 채 user2 로 다시 로그인한다
        MvcResult relogin = mvc.perform(post(U.login()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USER2, PASSWORD)).cookie(user1, admin))
                .andReturn();
        assertEquals(200, relogin.getResponse().getStatus());
        Cookie user2 = cookieOf(relogin, U);

        assertEquals(401, refresh(user1).getResponse().getStatus());    // 옛 토큰은 더 못 쓴다
        assertEquals(200, refresh(user2).getResponse().getStatus());
        assertEquals(200, refresh(admin).getResponse().getStatus());    // 관리자 세션은 무관
    }

    @Test
    @DisplayName("로그인에 실패하면 들고 있던 토큰을 건드리지 않는다")
    void 로그인_실패는_세션을_끊지_않는다() throws Exception {
        Cookie user = userCookie(USER);

        mvc.perform(post(U.login()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(USER, "wrong")).cookie(user))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));

        assertEquals(200, refresh(user).getResponse().getStatus());
    }

    @Test
    @DisplayName("다른 대상의 토큰은 무효이고 소비되지도 않는다 — 주인은 계속 쓸 수 있다")
    void 대상이_다른_토큰() throws Exception {
        Cookie user = userCookie(USER);
        Cookie admin = adminCookie(ADMIN);

        // 사용자 토큰을 관리자 쿠키 이름으로 관리자 refresh 에 보낸다 (그 반대도)
        mvc.perform(post(A.refresh()).header("Origin", origin).cookie(new Cookie(A.cookieName(), user.getValue())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401-1"));
        mvc.perform(post(U.refresh()).header("Origin", origin).cookie(new Cookie(U.cookieName(), admin.getValue())))
                .andExpect(status().isUnauthorized());

        // 다른 대상의 logout 으로도 지워지지 않는다
        mvc.perform(post(A.logout()).header("Origin", origin).cookie(new Cookie(A.cookieName(), user.getValue())))
                .andExpect(status().isNoContent());

        // 소비·삭제되지 않았으므로 주인은 그대로 갱신된다 (재사용 탐지에도 안 걸린다)
        assertEquals(200, refresh(user).getResponse().getStatus());
        assertEquals(200, refresh(admin).getResponse().getStatus());
    }

    @Test
    @DisplayName("logout 은 204 + 쿠키 삭제, 이후 그 토큰으로 refresh 불가, 쿠키 없어도 204")
    void 로그아웃() throws Exception {
        Cookie c = userCookie(USER);

        mvc.perform(post(U.logout()).header("Origin", origin).cookie(c))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(U.cookieName(), 0))
                .andExpect(cookie().path(U.cookieName(), U.basePath()));
        assertEquals(401, refresh(c).getResponse().getStatus());
        mvc.perform(post(U.logout()).header("Origin", origin)).andExpect(status().isNoContent());   // 멱등

        Cookie admin = adminCookie(ADMIN);
        mvc.perform(post(A.logout()).header("Origin", origin).cookie(admin))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(A.cookieName(), 0));
        assertEquals(401, refresh(admin).getResponse().getStatus());
    }

    @Test
    @DisplayName("CSRF: refresh/logout 은 허용된 Origin(또는 Referer)이 없으면 403(AUTH403-0)")
    void csrf_출처_검사() throws Exception {
        Cookie c = userCookie(USER);

        // ★ 네 경로 전부 — 목록은 AccountType 에서 온다. 경로를 옮겼는데 검사가 꺼지면 여기서 걸린다
        for (String path : AccountType.cookieOnlyPaths()) {
            mvc.perform(post(path).cookie(c))                                           // Origin·Referer 없음
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH403-0"));
            mvc.perform(post(path).cookie(c).header("Referer", "http://evil.example/page"))   // 허용 안 된 Referer
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH403-0"));
        }
        // 허용 안 된 Origin 은 그보다 앞선 CORS 필터가 거부한다
        mvc.perform(post(U.refresh()).cookie(c).header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());

        // 거부된 요청들이 토큰을 소모하지 않았다 — Referer 로 허용 Origin 을 대신해도 통과
        mvc.perform(post(U.refresh()).cookie(c).header("Referer", origin + "/admin/events"))
                .andExpect(status().isOk());
        // login 은 검사 대상이 아니다 (Origin 없이도 200)
        assertEquals(200, login(U.login(), USER, PASSWORD).getResponse().getStatus());
    }

    @Test
    @DisplayName("정리 배치는 만료된 Refresh Token 만 지우고 유효한 토큰은 남긴다")
    void 만료_토큰_정리() throws Exception {
        Long userId = userIdOf(USER);
        Instant now = Instant.now();
        refreshTokens.save(new RefreshToken(AuthUser.user(userId), "a".repeat(64), now.minusSeconds(60)));
        refreshTokens.save(new RefreshToken(AuthUser.user(userId), "b".repeat(64), now.minusSeconds(1)));
        Cookie valid = userCookie(USER);

        refreshTokenService.purgeExpired();

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, userId));
        assertEquals(200, refresh(valid).getResponse().getStatus());
    }

    @Test
    @DisplayName("CORS: 허용한 Origin 에만 credentials 허용")
    void cors() throws Exception {
        mvc.perform(options(U.refresh())
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        mvc.perform(options(U.refresh())
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
