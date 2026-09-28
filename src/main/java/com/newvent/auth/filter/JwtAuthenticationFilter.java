package com.newvent.auth.filter;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.newvent.auth.jwt.JwtProvider;

/**
 * Authorization: Bearer 를 검증해 SecurityContext 를 채운다.
 * 권한은 로그인한 테이블로 정해진다 — admins 면 ROLE_ADMIN, users 면 ROLE_USER.
 * 토큰이 없거나 무효하면 그냥 통과시키고, 401/403 판단은 SecurityConfig 의 인가 규칙에 맡긴다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER)) {
            jwtProvider.parse(header.substring(BEARER.length()).trim()).ifPresent(principal -> {
                var authority = new SimpleGrantedAuthority(principal.admin() ? ROLE_ADMIN : ROLE_USER);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        chain.doFilter(request, response);
    }
}
