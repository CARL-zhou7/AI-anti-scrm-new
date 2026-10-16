package com.aiscam.antiscam.auth;

import com.aiscam.antiscam.auth.domain.*;
import com.aiscam.antiscam.auth.service.AuthService;
import com.aiscam.antiscam.auth.web.AuthController;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.error.GlobalExceptionHandler;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import com.aiscam.antiscam.home.service.HomeService;
import com.aiscam.antiscam.home.web.HomeController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, HomeController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class, properties = {
        "app.security.session-cookie-secure=false", "spring.jackson.deserialization.fail-on-unknown-properties=true"})
@Import({SecurityConfig.class, JacksonConfig.class, AuthService.class, HomeService.class,
        RequestIdFilter.class, GlobalExceptionHandler.class})
class AuthHomeSmokeTest {
    private static final String BASE = "/anti.scam/v1";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserRepository users;

    @BeforeEach
    void user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(101L);
        when(user.getUsername()).thenReturn("李雷");
        when(user.getRole()).thenReturn(UserRole.USER);
        when(user.getPasswordHash()).thenReturn(encoder.encode("123456"));
        when(users.findByUsernameKey("李雷")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any())).thenReturn(user);
    }

    private MvcResult csrf(MockHttpSession session) throws Exception {
        var request = get(BASE + "/auth/csrf");
        if (session != null) request.session(session);
        return mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
    }

    private String token(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }

    @Test
    void registerIsAnonymousAndRejectsPrivilegedFieldsAndDuplicateNames() throws Exception {
        var initial = csrf(null);
        var session = (MockHttpSession) initial.getRequest().getSession();
        String token = token(initial);
        mvc.perform(post(BASE + "/auth/register").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"username":" 李雷 ","password":"123456","confirmPassword":"123456"}"""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.account.id").value("101"))
                .andExpect(jsonPath("$.data.nextAction").value("LOGIN"));
        mvc.perform(get(BASE + "/auth/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/auth/register").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"username":"李雷","password":"123456","confirmPassword":"123456","role":"ADMIN"}"""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(BASE + "/auth/register").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"username":101,"password":"123456","confirmPassword":"123456"}"""))
                .andExpect(status().isBadRequest());
        when(users.existsByUsernameKey("李雷")).thenReturn(true);
        mvc.perform(post(BASE + "/auth/register").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"username":"李雷","password":"123456","confirmPassword":"123456"}"""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void loginHomeMeAndLogoutWithRotatingCsrf() throws Exception {
        var initial = csrf(null);
        var session = (MockHttpSession) initial.getRequest().getSession();
        String oldId = session.getId();
        String oldToken = token(initial);
        mvc.perform(post(BASE + "/auth/login").session(session).header("X-CSRF-TOKEN", oldToken)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"李雷","password":"123456"}"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("USER"));
        assertNotEquals(oldId, session.getId());
        mvc.perform(get(BASE + "/auth/me?userId=999").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("101"));
        mvc.perform(get(BASE + "/home").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newsState").value("EMPTY"))
                .andExpect(jsonPath("$.data.entries[0]").value("CONFIGURATION"));
        mvc.perform(get(BASE + "/news").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post(BASE + "/auth/logout").session(session).header("X-CSRF-TOKEN", oldToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        String fresh = token(csrf(session));
        assertNotEquals(oldToken, fresh);
        mvc.perform(post(BASE + "/auth/logout").session(session).header("X-CSRF-TOKEN", fresh))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.loggedOut").value(true))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertTrue(session.isInvalid());
        mvc.perform(get(BASE + "/home")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCsrfAndAdminCannotUseUserEndpointsOrRegister() throws Exception {
        mvc.perform(post(BASE + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var admin = UsernamePasswordAuthenticationToken.authenticated(new Account("102", "admin", UserRole.ADMIN),
                null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        for (String path : List.of("/home", "/news")) {
            mvc.perform(get(BASE + path).with(authentication(admin)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mvc.perform(post(BASE + "/auth/register").with(authentication(admin))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        var user = UsernamePasswordAuthenticationToken.authenticated(new Account("101", "李雷", UserRole.USER),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mvc.perform(get(BASE + "/admin/scenarios").with(authentication(user)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unknownAccountAndWrongPasswordHaveSamePublicError() throws Exception {
        var initial = csrf(null);
        var session = (MockHttpSession) initial.getRequest().getSession();
        for (String username : List.of("李雷", "不存在")) {
            mvc.perform(post(BASE + "/auth/login").session(session).header("X-CSRF-TOKEN", token(initial))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", "wrong123"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("账号或密码错误"))
                    .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        }
    }
}
