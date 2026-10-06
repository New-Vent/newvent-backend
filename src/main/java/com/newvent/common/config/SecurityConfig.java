package com.newvent.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.newvent.auth.config.AuthProps;
import com.newvent.auth.config.ProdAuthGuard;
import com.newvent.auth.filter.CsrfOriginFilter;
import com.newvent.auth.filter.JwtAuthenticationFilter;
import com.newvent.auth.jwt.JwtProvider;
import com.newvent.auth.web.AccountType;

/**
 * 인가 규칙.
 *
 *   POST /api/auth/{login,refresh,logout},
 *        /api/admin/auth/{login,refresh,logout} public (AccountType.publicPaths())
 *   POST /api/public/users/signup             public
 *   GET  /api/public/events                   public (목록 조회)
 *   GET  /api/public/events/{id}              public
 *   /api/admin/**                             ADMIN  (admins 테이블로 로그인)
 *   /api/users/**                             USER   (본인 정보 — 토큰의 id 가 users.id 여야 한다)
 *   그 외 /api/**                             USER 또는 ADMIN
 *
 * ★ /api/public/** 을 통째로 열지 않는다. 공개할 엔드포인트만 하나씩 적는다 —
 *   public 아래에 새 API 를 두는 것만으로 인증 없이 열리는 일을 막는다. 적지 않은 것은 /api/** 규칙에 걸린다.
 *
 * 역할 컬럼/enum 은 없다 — 어느 테이블로 로그인했는지가 곧 권한이다 (JwtAuthenticationFilter).
 */
@Configuration
@EnableConfigurationProperties(AuthProps.class)
@Import(ProdAuthGuard.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider, AuthProps authProps,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            exceptionResolver.resolveException(req, res, null, ex);
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            exceptionResolver.resolveException(req, res, null, ex);
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // ★ /api/admin/** · /api/** 규칙보다 앞에 있어야 한다. 뒤에 두면 로그인하려면 로그인이 필요해진다
                        .requestMatchers(HttpMethod.POST, AccountType.publicPaths()).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/users/signup").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/events/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/e/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("USER")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new CsrfOriginFilter(authProps, exceptionResolver), JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AuthProps props) {
        var config = new CorsConfiguration();
        // 쿠키(credentials)를 허용하려면 Origin 을 "*" 로 둘 수 없다 — 명시한 것만 허용
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 비밀번호 해싱은 여기 한 곳에서만 정한다 — 회원가입(UserService)과 로그인(AuthServiceImpl)이 같이 쓴다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
