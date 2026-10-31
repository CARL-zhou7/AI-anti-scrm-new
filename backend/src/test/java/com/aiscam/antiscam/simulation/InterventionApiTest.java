package com.aiscam.antiscam.simulation;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.error.*;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import com.aiscam.antiscam.simulation.service.InterventionService;
import com.aiscam.antiscam.simulation.web.InterventionController;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.*;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.State;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API-47～49 boundary behaviour: role, ownership and the review phase (Phase 2). The service is mocked so a wrong
 * status or envelope is never hidden by business logic.
 */
@WebMvcTest(controllers = InterventionController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "app.security.session-cookie-secure=false")
@Import({SecurityConfig.class, JacksonConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class InterventionApiTest {
    private static final String PATH = "/anti.scam/v1/simulations/5/intervention";
    @Autowired MockMvc mvc;
    @MockitoBean InterventionService service;

    private UsernamePasswordAuthenticationToken auth(UserRole role, String id) {
        return new UsernamePasswordAuthenticationToken(new Account(id, "测试用户", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private static ScammerDraft draft() {
        return new ScammerDraft("11", "5", 2, "SCAMMER_REVIEW", 1, "DRAFT", "原始台词", null, "草稿台词",
                "发送材料", null, "");
    }

    private static InterventionState reviewing() {
        return new InterventionState("5", "ADMIN_TEST", 2, "SCAMMER_REVIEW", draft(), 0, true, true);
    }

    @Test
    void anonymousCallersCannotReachTheReviewGate() throws Exception {
        for (String path : List.of(PATH, PATH + "/adopt", PATH + "/regenerate")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryUsersCanReadEditRegenerateAndAdoptTheirDraft() throws Exception {
        when(service.state(7, 5)).thenReturn(reviewing());
        when(service.revise(eq(7L), eq(5L), any(DraftRequest.class))).thenReturn(reviewing());
        when(service.regenerate(eq(7L), eq(5L), any(DraftRequest.class))).thenReturn(reviewing());
        mvc.perform(get(PATH).with(authentication(auth(UserRole.USER, "7"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentDraft.draftId").value("11"));
        for (var request : List.of(put(PATH), post(PATH + "/regenerate"))) {
            mvc.perform(request.with(authentication(auth(UserRole.USER, "7"))).with(csrf().asHeader())
                    .contentType("application/json")
                    .content("{\"draftId\":\"11\",\"expectedRevisionNo\":1}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(PATH + "/adopt").with(authentication(auth(UserRole.USER, "7"))).with(csrf().asHeader())
                .contentType("application/json")
                .content("{\"draftId\":\"11\",\"expectedRevisionNo\":1,\"continue\":true}"))
                .andExpect(status().isOk());
        verify(service).adopt(eq(7L), eq(5L), any(AdoptRequest.class));
    }

    @Test
    void ordinaryUsersCannotReadSomeoneElsesDraftOrWriteWithoutCsrf() throws Exception {
        when(service.state(8, 5)).thenThrow(ApiException.notFound());
        mvc.perform(get(PATH).with(authentication(auth(UserRole.USER, "8"))))
                .andExpect(status().isNotFound());
        mvc.perform(post(PATH + "/adopt").with(authentication(auth(UserRole.USER, "7")))
                .contentType("application/json").content("{\"draftId\":\"11\",\"expectedRevisionNo\":1,\"continue\":true}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        verify(service, never()).adopt(anyLong(), anyLong(), any());
    }

    @Test
    void anotherAdministratorsDemoSessionIsANotFound() throws Exception {
        when(service.state(7, 5)).thenThrow(ApiException.notFound());
        mvc.perform(get(PATH).with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(service).state(7, 5);
    }

    @Test
    void administratorAliasPreservesSessionOwnership() throws Exception {
        when(service.state(7, 5)).thenThrow(ApiException.notFound());
        mvc.perform(get("/anti.scam/v1/admin/simulations/5/intervention").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(service).state(7, 5);
    }

    @Test
    void openingRoundHasNoPhaseAndNoDraft() throws Exception {
        when(service.state(7, 5)).thenReturn(new InterventionState("5", "ADMIN_TEST", 0, null, null, 0, false, false));
        mvc.perform(get(PATH).with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.phase").doesNotExist())
                .andExpect(jsonPath("$.data.currentRoundNo").value(0))
                .andExpect(jsonPath("$.data.currentDraft").doesNotExist())
                .andExpect(jsonPath("$.data.canAdopt").value(false));
    }

    @Test
    void generatingRoundHasNoDraftAndReviewRoundHasOne() throws Exception {
        when(service.state(7, 5)).thenReturn(new InterventionState("5", "ADMIN_TEST", 2,
                "SCAMMER_GENERATING", null, 0, false, false));
        mvc.perform(get(PATH).with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("SCAMMER_GENERATING"))
                .andExpect(jsonPath("$.data.currentDraft").doesNotExist());

        when(service.state(7, 5)).thenReturn(reviewing());
        mvc.perform(get(PATH).with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.phase").value("SCAMMER_REVIEW"))
                .andExpect(jsonPath("$.data.currentDraft.draftId").value("11"))
                .andExpect(jsonPath("$.data.currentDraft.draftContent").value("草稿台词"))
                .andExpect(jsonPath("$.data.canAdopt").value(true));
    }

    @Test
    void adoptedStateIsReturnedWithTheWireNameContinue() throws Exception {
        when(service.adopt(eq(7L), eq(5L), any(AdoptRequest.class)))
                .thenReturn(new State("5", "RUNNING", "9", "", null, null, 2, null, null, null, null, null, null, null,
                        false, null, 0, null, null, null, null, null, 0));
        mvc.perform(post(PATH + "/adopt").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"draftId\":\"11\",\"expectedRevisionNo\":1,\"continue\":true,\"content\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sessionId").value("5"));
        // The Java keyword is not the wire name: the request field stays "continue".
        var captor = org.mockito.ArgumentCaptor.forClass(AdoptRequest.class);
        verify(service).adopt(eq(7L), eq(5L), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, captor.getValue().continueAdopt());
    }

    @Test
    void contentFailureIsAFieldErrorAndStaleDraftsAre409() throws Exception {
        when(service.adopt(eq(7L), eq(5L), any(AdoptRequest.class)))
                .thenThrow(ApiException.field("content", "CONTENT_REJECTED", "内容与上一轮骗子台词完全相同"));
        mvc.perform(post(PATH + "/adopt").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"draftId\":\"11\",\"expectedRevisionNo\":1,\"continue\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("content"))
                .andExpect(jsonPath("$.data.fieldErrors[0].code").value("CONTENT_REJECTED"));

        when(service.adopt(eq(7L), eq(5L), any(AdoptRequest.class))).thenThrow(ApiException.staleDraft());
        mvc.perform(post(PATH + "/adopt").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json")
                        .content("{\"draftId\":\"11\",\"expectedRevisionNo\":1,\"continue\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_DRAFT"));

        when(service.regenerate(eq(7L), eq(5L), any(DraftRequest.class))).thenThrow(ApiException.draftRevisionConflict());
        mvc.perform(post(PATH + "/regenerate").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"draftId\":\"11\",\"expectedRevisionNo\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRAFT_REVISION_CONFLICT"));
    }

    @Test
    void mutationRequiresCsrfAndUnexpectedQueryIsRejected() throws Exception {
        mvc.perform(post(PATH + "/adopt").with(authentication(auth(UserRole.ADMIN, "7")))
                        .contentType("application/json").content("{\"draftId\":\"11\",\"expectedRevisionNo\":1,\"continue\":true}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(get(PATH + "?x=1").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void nonNumericSessionIdIsANotFound() throws Exception {
        mvc.perform(get("/anti.scam/v1/simulations/abc/intervention").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
