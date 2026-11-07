package com.aiscam.antiscam.auth;

import com.aiscam.antiscam.auth.domain.*;
import com.aiscam.antiscam.auth.service.AuthService;
import com.aiscam.antiscam.auth.web.AuthController;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.admin.service.AdminScenarioService;
import com.aiscam.antiscam.admin.web.AdminScenarioController;
import com.aiscam.antiscam.common.error.GlobalExceptionHandler;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import com.aiscam.antiscam.home.service.HomeService;
import com.aiscam.antiscam.home.web.HomeController;
import com.aiscam.antiscam.simulation.web.SimulationController;
import com.aiscam.antiscam.simulation.service.SimulationService;
import com.aiscam.antiscam.simulation.service.SimulationTransitions;
import com.aiscam.antiscam.simulation.engine.SimulationOrchestrator;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.aiscam.antiscam.content.web.ContentController;
import com.aiscam.antiscam.content.service.ContentService;
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

@WebMvcTest(controllers = {AuthController.class, HomeController.class, AdminScenarioController.class, SimulationController.class, ContentController.class},
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
    @MockitoBean AdminScenarioService scenarios;
    @MockitoBean SimulationService simulations;
    @MockitoBean SimulationOrchestrator orchestrator;
    @MockitoBean SimulationEventHub hub;
    @MockitoBean ContentService content;

    @BeforeEach
    void user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(101L);
        when(user.getUsername()).thenReturn("李雷");
        when(user.getRole()).thenReturn(UserRole.USER);
        when(user.getPasswordHash()).thenReturn(encoder.encode("123456"));
        when(users.findByUsernameKey("李雷")).thenReturn(Optional.of(user));
        when(users.saveAndFlush(any())).thenReturn(user);
        User admin = mock(User.class);
        when(admin.getId()).thenReturn(102L);
        when(admin.getUsername()).thenReturn("admin");
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(admin.getPasswordHash()).thenReturn(encoder.encode("123456"));
        when(users.findByUsernameKey("admin")).thenReturn(Optional.of(admin));
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
    void userAndAdminCanShareBrowserWithoutReplacingEachOther() throws Exception {
        for (boolean adminFirst : List.of(true, false)) {
            var initial = csrf(null);
            var session = (MockHttpSession) initial.getRequest().getSession();
            String userToken = token(initial);
            for (String username : adminFirst ? List.of("admin", "李雷") : List.of("李雷", "admin")) {
                mvc.perform(post(BASE + "/auth/login").session(session).header("X-CSRF-TOKEN", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", "123456"))))
                        .andExpect(status().isOk());
                userToken = token(csrf(session));
            }
            mvc.perform(get(BASE + "/auth/me").session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("USER"));
            mvc.perform(get(BASE + "/auth/me").session(session).header("X-Login-Role", "ADMIN"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ADMIN"));
            mvc.perform(get(BASE + "/home").session(session).header("X-Login-Role", "ADMIN"))
                    .andExpect(status().isOk());
            String adminToken = token(mvc.perform(get(BASE + "/auth/csrf").session(session)
                    .header("X-Login-Role", "ADMIN")).andExpect(status().isOk()).andReturn());
            mvc.perform(get(BASE + "/admin/scenarios").session(session)).andExpect(status().isOk());
            mvc.perform(put(BASE + "/admin/scenarios/1").session(session).header("X-CSRF-TOKEN", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"亲友借款\",\"description\":\"更新简介\",\"version\":0}"))
                    .andExpect(status().isOk());
            mvc.perform(post(BASE + "/auth/logout").session(session).header("X-Login-Role", "ADMIN")
                    .header("X-CSRF-TOKEN", userToken)).andExpect(status().isForbidden());
            mvc.perform(post(BASE + "/auth/logout").session(session).header("X-Login-Role", "ADMIN")
                    .header("X-CSRF-TOKEN", adminToken)).andExpect(status().isOk());
            assertFalse(session.isInvalid());
            mvc.perform(get(BASE + "/home").session(session)).andExpect(status().isOk());
            mvc.perform(get(BASE + "/auth/me").session(session).header("X-Login-Role", "ADMIN"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(BASE + "/auth/logout").session(session).header("X-CSRF-TOKEN", userToken))
                    .andExpect(status().isOk());
            assertTrue(session.isInvalid());
        }
    }

    private MockHttpSession signIn(MockHttpSession session, String username) throws Exception {
        var initial = csrf(session);
        session = (MockHttpSession) initial.getRequest().getSession();
        mvc.perform(post(BASE + "/auth/login").session(session).header("X-CSRF-TOKEN", token(initial))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk());
        return session;
    }

    @Test void adminDemoUsesItsOwnLoginAndCsrfEvenWhenAUserSharesTheCookie() throws Exception {
        var session = signIn(null, "admin");
        mvc.perform(get(BASE + "/scenarios").session(session)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/scammer-profiles/1").session(session)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/victim-profiles/1").session(session)).andExpect(status().isOk());
        when(content.avatar("portrait", 102L)).thenReturn(new com.aiscam.antiscam.content.avatar.AvatarResource(new byte[]{1}, "image/png"));
        mvc.perform(get(BASE + "/avatars/portrait").session(session)).andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1}));
        mvc.perform(get(BASE + "/home").session(session)).andExpect(status().isUnauthorized());
        String adminToken = token(mvc.perform(get(BASE + "/auth/csrf").session(session)
                .header("X-Login-Role", "ADMIN")).andReturn());
        when(simulations.start(eq(102L), eq(UserRole.ADMIN), anyString(), any()))
                .thenReturn(new SimulationTransitions.StartResult(true, null));
        String body = "{\"startRequestId\":\"test\",\"scenarioId\":\"1\",\"scammerProfileId\":\"1\",\"victimProfileId\":\"1\"}";
        mvc.perform(post(BASE + "/admin/simulations").session(session).header("X-CSRF-TOKEN", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        session = signIn(session, "李雷");
        String userToken = token(csrf(session));
        mvc.perform(post(BASE + "/admin/simulations").session(session).header("X-CSRF-TOKEN", userToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post(BASE + "/admin/simulations").session(session).header("X-CSRF-TOKEN", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        when(simulations.audio(102L, "12", "3")).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get(BASE + "/admin/simulations/12/messages/3/audio").session(session))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3}));
        var state = mock(com.aiscam.antiscam.simulation.web.dto.SimulationViews.State.class);
        when(state.stateVersion()).thenReturn("1");
        when(simulations.ownedId(102L, "12")).thenReturn(12L);
        when(simulations.sessionState(102L, 12L)).thenReturn(new com.aiscam.antiscam.simulation.web.dto.SimulationViews.SessionStateData(state, null));
        when(hub.subscribe(eq(12L), anyString(), any())).thenReturn(new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());
        mvc.perform(get(BASE + "/admin/simulations/12/events").session(session)).andExpect(status().isOk());
        verify(simulations).sessionState(102L, 12L);
        verify(simulations, times(2)).start(eq(102L), eq(UserRole.ADMIN), anyString(), any());
        var userOnly = signIn(null, "李雷");
        mvc.perform(get(BASE + "/admin/simulations/12").session(userOnly)).andExpect(status().isUnauthorized());
    }

    @Test void userLogoutAndAdminReloginLeaveTheOtherRoleWorking() throws Exception {
        var session = signIn(signIn(null,"李雷"),"admin");
        String adminToken = token(mvc.perform(get(BASE + "/auth/csrf").session(session)
                .header("X-Login-Role","ADMIN")).andReturn());
        mvc.perform(post(BASE + "/auth/logout").session(session).header("X-CSRF-TOKEN",token(csrf(session))))
                .andExpect(status().isOk());
        assertFalse(session.isInvalid());
        mvc.perform(get(BASE + "/admin/scenarios").session(session)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/home").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/auth/logout").session(session).header("X-Login-Role","ADMIN")
                .header("X-CSRF-TOKEN",adminToken)).andExpect(status().isOk());
        assertTrue(session.isInvalid());

        var original = signIn(signIn(null,"李雷"),"admin");
        var elsewhere = signIn(null,"admin");
        mvc.perform(get(BASE + "/auth/me").session(original).header("X-Login-Role","ADMIN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/home").session(original)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/admin/scenarios").session(elsewhere)).andExpect(status().isOk());
        mvc.perform(get(BASE + "/admin/scenarios").session(original)).andExpect(status().isUnauthorized());
        signIn(null,"李雷");
        mvc.perform(get(BASE + "/home").session(original)).andExpect(status().isUnauthorized());
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
                .andExpect(jsonPath("$.data.newsState").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.entries[0]").value("CONFIGURATION"));
        mvc.perform(get(BASE + "/news").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].publishedAt").value("2026-11-04"))
                .andExpect(jsonPath("$.data[0].category").value("真实案例"))
                .andExpect(jsonPath("$.data[0].url").value("https://gaj.wuhan.gov.cn/jmzx/jwdt/202611/t20261104_2873716.html"));
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
        var initial = csrf(null);
        mvc.perform(post(BASE + "/auth/register").session((MockHttpSession) initial.getRequest().getSession())
                .with(authentication(admin)).header("X-CSRF-TOKEN", token(initial)))
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
