package com.newvent.auth.filter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.newvent.auth.config.AuthProps;
import com.newvent.auth.exception.CsrfOriginException;
import com.newvent.auth.web.AccountType;

/**
 * refresh · logout 은 Authorization 헤더 없이 Refresh Token 쿠키만으로 동작한다.
 *   /api/auth/refresh, /api/auth/logout, /api/admin/auth/refresh, /api/admin/auth/logout
 * 브라우저는 교차 사이트 요청에도 쿠키를 자동으로 실어보내므로(CSRF), SameSite 쿠키 속성만으로는
 * 완전히 막을 수 없다 — 특히 auth.cookie.same-site=None 배포에서는 사실상 무방비다.
 *
 * 이 필터는 위 엔드포인트에 한해 Origin(없으면 Referer) 헤더가 auth.cors.allowed-origins 에
 * 있는지 확인한다. 값이 없거나 목록에 없으면 요청을 거부한다 (fail-closed).
 *
 * 주의:
 *  - login 은 대상이 아니다 — 쿠키 없이 이메일/비밀번호로 인증하므로 공격자가 결과를
 *    가로챌 수 없고, 이 필터가 막으려는 "쿠키 재생" 유형의 CSRF 와는 성격이 다르다.
 *  - 같은 오리진에서만 서비스한다면(백엔드가 프론트를 직접 서빙) auth.cors.allowed-origins 에
 *    그 오리진을 반드시 넣어야 한다 — CORS 목적이 아니어도 이 필터가 재사용한다.
 *
 * ★ @Component 로 만들지 않는다 — SecurityConfig 에서 순서를 보장해 직접 등록한다.
 */
public class CsrfOriginFilter extends OncePerRequestFilter {

    /**
     * ★ 경로를 여기 직접 적지 않는다. 인증 경로가 바뀌었는데 이 목록만 옛 값이면
     *   검사가 에러 없이 꺼진다(fail-open). AccountType 한 곳에서 받아온다.
     */
    private static final Set<String> PROTECTED_PATHS = Set.copyOf(AccountType.cookieOnlyPaths());

    private final List<String> allowedOrigins;
    private final HandlerExceptionResolver exceptionResolver;

    public CsrfOriginFilter(AuthProps props, HandlerExceptionResolver exceptionResolver) {
        this.allowedOrigins = props.cors().allowedOrigins();
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!requiresCheck(request)) {
            chain.doFilter(request, response);
            return;
        }

        String origin = resolveOrigin(request);
        if (origin == null || !allowedOrigins.contains(origin)) {
            reject(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 경로는 getServletPath() 가 아니라 requestURI - contextPath 로 판별한다.
     * getServletPath() 는 서블릿 매핑에 따라 값이 달라지고(MockMvc 에서는 "" 라 필터가 통째로 건너뛰어졌다),
     * 그러면 검사가 조용히 꺼진다 — 이 필터는 fail-closed 여야 한다.
     */
    private boolean requiresCheck(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PROTECTED_PATHS.contains(path);
    }

    /** Origin 헤더가 없으면(구형 브라우저 등) Referer 로 대체한다. */
    private String resolveOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null) {
            return origin;
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 응답 형식은 GlobalExceptionHandler 가 정한다 — 필터는 컨트롤러 밖이라 예외를 던져도
     * @RestControllerAdvice 에 닿지 않으므로 HandlerExceptionResolver 로 직접 넘긴다.
     * 상태를 먼저 403 으로 찍어 둬서, 혹시 해석되지 않아도 요청이 통과한 것처럼 보이지 않게 한다.
     */
    private void reject(HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        exceptionResolver.resolveException(request, response, null, new CsrfOriginException());
    }
}
