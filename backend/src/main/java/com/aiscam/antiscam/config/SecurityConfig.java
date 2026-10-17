package com.aiscam.antiscam.config;

import com.aiscam.antiscam.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.session.HttpSessionEventPublisher;

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
    HttpSessionCsrfTokenRepository csrfRepository() {
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    HttpSessionSecurityContextRepository contextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionRegistry sessionRegistry() { return new SessionRegistryImpl(); }

    @Bean
    HttpSessionEventPublisher sessionEventPublisher() { return new HttpSessionEventPublisher(); }

    @Bean
    SessionAuthenticationStrategy loginStrategy(SessionRegistry registry, HttpSessionCsrfTokenRepository csrf) {
        var concurrent = new ConcurrentSessionControlAuthenticationStrategy(registry);
        concurrent.setMaximumSessions(1);
        return new CompositeSessionAuthenticationStrategy(List.of(concurrent,
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrf),
                new RegisterSessionAuthenticationStrategy(registry)));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper,
            HttpSessionCsrfTokenRepository csrf, HttpSessionSecurityContextRepository context,
            SessionRegistry registry) throws Exception {
        http.csrf(c -> c.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(c -> c.securityContextRepository(context))
                .authorizeHttpRequests(a -> a
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
                                "/anti.scam/v1/avatars/**").hasRole("USER")
                        .requestMatchers("/anti.scam/v1/scenarios", "/anti.scam/v1/scammer-profiles/**",
                                "/anti.scam/v1/victim-profiles/**", "/anti.scam/v1/avatars/**").denyAll()
                        .requestMatchers("/anti.scam/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/anti.scam/v1/simulations/**", "/anti.scam/v1/history/**",
                                "/anti.scam/v1/timing/**").hasRole("USER")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(mapper, req, res, 401,
                                "UNAUTHENTICATED", "请先登录"))
                        .accessDeniedHandler((req, res, ex) -> {
                            boolean invalidCsrf = ex instanceof CsrfException;
                            writeError(mapper, req, res, 403, invalidCsrf ? "CSRF_INVALID" : "FORBIDDEN",
                                    invalidCsrf ? "安全令牌已失效，请重新提交" : "无权访问此功能");
                        }))
                .sessionManagement(s -> s.maximumSessions(1).sessionRegistry(registry)
                        .expiredSessionStrategy(event -> writeError(mapper, event.getRequest(),
                                event.getResponse(), 401, "UNAUTHENTICATED", "登录已失效，请重新登录")))
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
