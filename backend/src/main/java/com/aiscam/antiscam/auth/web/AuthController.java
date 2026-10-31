package com.aiscam.antiscam.auth.web;

import com.aiscam.antiscam.auth.service.AuthService;
import com.aiscam.antiscam.auth.service.LoginSessions;
import com.aiscam.antiscam.auth.web.dto.*;
import com.aiscam.antiscam.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/anti.scam/v1/auth")
public class AuthController {
    private final AuthService service;
    private final LoginSessions sessions;
    private final boolean secure;

    public AuthController(AuthService service, LoginSessions sessions,
            @Value("${app.security.session-cookie-secure:true}") boolean secure) {
        this.service = service;
        this.sessions = sessions;
        this.secure = secure;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfToken> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return ApiResponse.ok(new CsrfToken(token.getToken(), token.getHeaderName()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Registration> register(@RequestBody RegisterRequest body) {
        return ApiResponse.ok(new Registration(service.register(body.username(), body.password(),
                body.confirmPassword()), "LOGIN"));
    }

    @PostMapping("/login")
    public ApiResponse<Account> login(@RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Account account = service.authenticate(body.username(), body.password());
        var auth = UsernamePasswordAuthenticationToken.authenticated(account, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
        sessions.login(auth, request, response);
        return ApiResponse.ok(account);
    }

    @GetMapping("/me")
    public ApiResponse<Account> me(Authentication authentication) {
        return ApiResponse.ok((Account) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResult> logout(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        if (sessions.logout(request, response)) response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("JSESSIONID", "")
                .path("/anti.scam/v1").httpOnly(true).sameSite("Lax").secure(secure).maxAge(0).build().toString());
        return ApiResponse.ok(new LogoutResult(true));
    }
}
