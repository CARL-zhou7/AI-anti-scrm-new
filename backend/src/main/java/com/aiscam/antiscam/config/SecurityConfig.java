package com.aiscam.antiscam.config;

import com.aiscam.antiscam.common.api.ApiResponse;
import com.aiscam.antiscam.auth.service.LoginSessions;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        // PBKDF2 preserves all 24 Unicode code points, including >72-byte passwords.
        String id = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(id,
                Map.of(id, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    LoginSessions loginSessions(org.springframework.context.ApplicationEventPublisher events) {
        return new LoginSessions(events);
    }

    @Bean
    HttpSessionEventPublisher sessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper,
            LoginSessions sessions) throws Exception {
        http.csrf(c -> c.csrfTokenRepository(sessions)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(c -> c.securityContextRepository(sessions))
                .authorizeHttpRequests(a -> a
                        // SSE completes on async/error dispatches; URL rules below are unchanged.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC, jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(req -> req.getRequestURI().contains("..")
                                || req.getRequestURI().contains("\\") || req.getRequestURI().indexOf('\0') >= 0).denyAll()
                        .requestMatchers("/anti.scam/v1/auth/csrf", "/anti.scam/v1/auth/login").permitAll()
                        .requestMatchers("/anti.scam/v1/auth/register").access((authentication, request) ->
                                new org.springframework.security.authorization.AuthorizationDecision(
                                        authentication.get().getAuthorities().stream()
                                                .noneMatch(role -> role.getAuthority().equals("ROLE_ADMIN"))))
                        .requestMatchers("/anti.scam/v1/home", "/anti.scam/v1/news").hasRole("USER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/anti.scam/v1/scenarios",
                                "/anti.scam/v1/scammer-profiles/**", "/anti.scam/v1/victim-profiles/**",
                                "/anti.scam/v1/avatars/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/anti.scam/v1/scenarios", "/anti.scam/v1/scammer-profiles/**",
                                "/anti.scam/v1/victim-profiles/**", "/anti.scam/v1/avatars/**").denyAll()
                        .requestMatchers("/anti.scam/v1/admin/**").hasRole("ADMIN")
                        // Personal history stays USER-only: the list and the detail view of the caller's own runs.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/anti.scam/v1/simulations",
                                "/anti.scam/v1/simulations/*/detail").hasRole("USER")
                        // Private testing exposes round review to both roles; the service still checks ownership.
                        .requestMatchers("/anti.scam/v1/simulations/*/intervention",
                                "/anti.scam/v1/simulations/*/intervention/**").hasAnyRole("USER", "ADMIN")
                        // V1.4: an administrator runs their own ADMIN_TEST session; the service checks ownership.
                        .requestMatchers("/anti.scam/v1/simulations/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/anti.scam/v1/history/**", "/anti.scam/v1/timing/**").hasRole("USER")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(mapper, req, res, 401,
                                "UNAUTHENTICATED", "请先登录"))
                        .accessDeniedHandler((req, res, ex) -> {
                            boolean invalidCsrf = ex instanceof CsrfException;
                            writeError(mapper, req, res, 403, invalidCsrf ? "CSRF_INVALID" : "FORBIDDEN",
                                    invalidCsrf ? "安全令牌已失效，请重新提交" : "无权访问此功能");
                        }))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                            FilterChain chain) throws ServletException, IOException {
                        if (sessions.expired(req)) {
                            sessions.logout(req, res);
                            writeError(mapper, req, res, 401, "UNAUTHENTICATED", "登录已失效，请重新登录");
                            return;
                        }
                        chain.doFilter(req, res);
                    }
                }, AuthorizationFilter.class)
                .formLogin(c -> c.disable()).httpBasic(c -> c.disable())
                .logout(c -> c.disable()).requestCache(c -> c.disable());
        return http.build();
    }

    private static void writeError(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res,
            int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        mapper.writeValue(res.getOutputStream(),
                new ApiResponse<>(code, message, null, (String) req.getAttribute("requestId")));
    }
}
