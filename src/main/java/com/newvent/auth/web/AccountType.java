package com.newvent.auth.web;

import java.util.Arrays;
import java.util.List;

import com.newvent.auth.dto.AuthUser;

/**
 * 계정 종류 — 사용자(users) / 관리자(admins). 인증 경로와 Refresh 쿠키를 이것으로 나눈다.
 * **경로·쿠키 이름의 유일한 출처다.**
 *
 *   USER    /api/auth/{login, refresh, logout}         쿠키 nv_user_rt   Path=/api/auth
 *   ADMIN   /api/admin/auth/{login, refresh, logout}   쿠키 nv_admin_rt  Path=/api/admin/auth
 *
 * ★ 왜 둘로 나누나
 *   사용자 화면(/)과 관리자 화면(/admin/)은 같은 도메인이다. 쿠키는 포트도 가리지 않아서
 *   로컬(localhost:5173 · 5174)에서도 같은 통을 쓴다. 쿠키가 하나면 관리자로 로그인하는 순간
 *   사용자 세션을 덮어쓰고, 사용자 화면이 갱신하면 관리자 토큰을 받아 간다.
 *   이름과 Path 를 갈라 두면 브라우저가 각 refresh 요청에 자기 쿠키만 싣는다.
 *
 * ★ 왜 /api 아래인가
 *   nginx 와 Vite 개발 프록시가 /api/ 만 백엔드로 넘긴다. /auth 에 두면 요청이 백엔드에 닿지 않는다.
 *
 * ★ 경로는 환경마다 달라지지 않는다
 *   로컬과 배포가 같은 경로·같은 쿠키 이름을 써야 로컬에서 통과한 게 배포에서도 통과한다.
 *   환경마다 다른 건 Secure · 허용 Origin · 비밀키뿐이다 (auth.* 설정).
 *
 * ★ 여기를 바꾸면 컨트롤러 매핑(AuthPaths), 인가 규칙(SecurityConfig), CSRF 검사(CsrfOriginFilter)가
 *   모두 따라온다. 경로 문자열을 다른 파일에 다시 적지 말 것.
*/
public enum AccountType {

    USER(AuthPaths.USER, "nv_user_rt", false),
    ADMIN(AuthPaths.ADMIN, "nv_admin_rt", true);

    private final String basePath;
    private final String cookieName;
    private final boolean admin;

    AccountType(String basePath, String cookieName, boolean admin) {
        this.basePath = basePath;
        this.cookieName = cookieName;
        this.admin = admin;
    }

    /** Refresh 쿠키의 Path 이기도 하다 — refresh · logout 요청에만 실린다 */
    public String basePath()   { return basePath; }
    public String cookieName() { return cookieName; }
    public boolean admin()     { return admin; }

    public String login()   { return basePath + AuthPaths.LOGIN; }
    public String refresh() { return basePath + AuthPaths.REFRESH; }
    public String logout()  { return basePath + AuthPaths.LOGOUT; }

    public static AccountType of(AuthUser principal) {
        return principal.admin() ? ADMIN : USER;
    }

    /** 토큰 없이 부를 수 있는 경로 — login · refresh · logout 전부 */
    public static String[] publicPaths() {
        return Arrays.stream(values())
                .flatMap(a -> List.of(a.login(), a.refresh(), a.logout()).stream())
                .toArray(String[]::new);
    }

    /** Refresh 쿠키만으로 동작하는 경로 — CSRF(Origin) 검사 대상 */
    public static List<String> cookieOnlyPaths() {
        return Arrays.stream(values())
                .flatMap(a -> List.of(a.refresh(), a.logout()).stream())
                .toList();
    }
}
