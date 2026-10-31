package com.aiscam.antiscam.simulation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.common.error.GlobalExceptionHandler;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import com.aiscam.antiscam.simulation.service.SceneRuleService;
import com.aiscam.antiscam.simulation.web.AdminInterventionController;
import com.aiscam.antiscam.simulation.web.SceneRuleController;
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.InterventionRecord;
import com.aiscam.antiscam.simulation.web.dto.SceneRuleViews.*;
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
 * API-50～54 boundary behaviour (Phase 3, Task 12): the knowledge base is administrator-only, every failure keeps its
 * documented status code, and a rule failure can never be presented as a successful入库.
 */
@WebMvcTest(controllers = {SceneRuleController.class, AdminInterventionController.class},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "app.security.session-cookie-secure=false")
@Import({SecurityConfig.class, JacksonConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class SceneRuleApiTest {
    private static final String RULES = "/anti.scam/v1/admin/scene-rules";
    private static final String RECORDS = "/anti.scam/v1/admin/interventions";

    @Autowired MockMvc mvc;
    @MockitoBean SceneRuleService service;

    private UsernamePasswordAuthenticationToken auth(UserRole role, String id) {
        return new UsernamePasswordAuthenticationToken(new Account(id, "测试用户", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private static SceneRuleView ruleView(int version, String status, long sourceCount) {
        return new SceneRuleView("42", "FAKE_INVESTMENT", "称呼与事实一致", "始终沿用既定称谓",
                version, status, sourceCount, "");
    }

    private static InterventionRecord record(String id, String ruleId) {
        return new InterventionRecord(id, "100", "100", 2, "FAKE_INVESTMENT", "模型原台词", "最终台词",
                "不要让受害者再被称呼为老板", "LINKED", "", ruleId, "骗子：请问如何称呼？\n受害者：叫我小周。");
    }

    @Test
    void ordinaryUsersCannotReachTheKnowledgeBase() throws Exception {
        mvc.perform(get(RULES)).andExpect(status().isUnauthorized());
        mvc.perform(get(RULES).with(authentication(auth(UserRole.USER, "7")))).andExpect(status().isForbidden());
        mvc.perform(post(RULES).with(authentication(auth(UserRole.USER, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(RULES + "/42").with(authentication(auth(UserRole.USER, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(RULES + "/42/sources").with(authentication(auth(UserRole.USER, "7"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(RECORDS).with(authentication(auth(UserRole.USER, "7")))).andExpect(status().isForbidden());
        mvc.perform(post(RECORDS + "/induce").with(authentication(auth(UserRole.USER, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"interventionIds\":[\"1\"]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anAdministratorListsRulesWithStatusAndSourceCount() throws Exception {
        when(service.list()).thenReturn(List.of(ruleView(2, "ENABLED", 3)));
        mvc.perform(get(RULES).with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].ruleId").value("42"))
                .andExpect(jsonPath("$.data.items[0].version").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("ENABLED"))
                .andExpect(jsonPath("$.data.items[0].sourceCount").value(3));
    }

    @Test
    void creatingARuleAnswers201AndKeepsDocumentedFailures() throws Exception {
        when(service.create(eq(7L), any(SceneRuleWrite.class))).thenReturn(ruleView(1, "ENABLED", 2));
        mvc.perform(post(RULES).with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"scenarioCode\":\"FAKE_INVESTMENT\","
                                + "\"title\":\"称呼与事实一致\",\"ruleText\":\"始终沿用既定称谓\","
                                + "\"sourceInterventionIds\":[\"1\",\"2\"]}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.ruleId").value("42"));

        when(service.create(eq(7L), any(SceneRuleWrite.class)))
                .thenThrow(new ApiException(409, "RULE_TITLE_CONFLICT", "同一场景已有同名规则"));
        mvc.perform(post(RULES).with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"scenarioCode\":\"FAKE_INVESTMENT\","
                                + "\"title\":\"称呼与事实一致\",\"ruleText\":\"正文\",\"sourceInterventionIds\":[\"1\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RULE_TITLE_CONFLICT"));

        when(service.create(eq(7L), any(SceneRuleWrite.class)))
                .thenThrow(new ApiException(409, "SOURCE_ALREADY_LINKED", "该来源已归入其他规则"));
        mvc.perform(post(RULES).with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"scenarioCode\":\"FAKE_INVESTMENT\","
                                + "\"title\":\"称呼与事实一致\",\"ruleText\":\"正文\",\"sourceInterventionIds\":[\"1\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOURCE_ALREADY_LINKED"));
    }

    @Test
    void editingARuleReportsVersionConflictsAndFieldErrors() throws Exception {
        when(service.update(eq(7L), eq(42L), any(SceneRuleUpdate.class))).thenReturn(ruleView(2, "DISABLED", 3));
        mvc.perform(put(RULES + "/42").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"title\":\"称呼与事实一致\",\"status\":\"DISABLED\",\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.version").value(2));

        when(service.update(eq(7L), eq(42L), any(SceneRuleUpdate.class))).thenThrow(ApiException.versionConflict(2));
        mvc.perform(put(RULES + "/42").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"status\":\"DISABLED\",\"version\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.data.currentVersion").value(2));

        when(service.update(eq(7L), eq(42L), any(SceneRuleUpdate.class)))
                .thenThrow(ApiException.field("status", "VALUE_INVALID", "状态只允许ENABLED或DISABLED"));
        mvc.perform(put(RULES + "/42").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("status"));
    }

    @Test
    void sourcesOfOneRuleAreReadByRuleId() throws Exception {
        when(service.sources(7L, 42)).thenReturn(List.of(record("1", "42"), record("2", "42")));
        mvc.perform(get(RULES + "/42/sources").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].interventionId").value("1"))
                .andExpect(jsonPath("$.data[0].ruleId").value("42"))
                .andExpect(jsonPath("$.data[0].finalContent").value("最终台词"));
        verify(service).sources(7L, 42);
    }

    @Test
    void waitingRecordsCanBeFilteredBySceneOrByRule() throws Exception {
        when(service.waitingRecords(7L, "FAKE_LOAN")).thenReturn(List.of(record("3", null)));
        mvc.perform(get(RECORDS + "?sceneCode=FAKE_LOAN").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].interventionId").value("3"))
                .andExpect(jsonPath("$.data[0].ruleId").doesNotExist());
        verify(service).waitingRecords(7L, "FAKE_LOAN");

        when(service.sources(7L, 42)).thenReturn(List.of(record("1", "42")));
        mvc.perform(get(RECORDS + "?ruleId=42").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].ruleId").value("42"));
        verify(service).sources(7L, 42);
    }

    @Test
    void inductionReturnsAProposalWithoutWritingAnything() throws Exception {
        when(service.induce(7L, List.of("1", "2"))).thenReturn(new RuleDraft("FAKE_INVESTMENT", "归纳标题",
                "· 第一条\n· 第二条", List.of("1", "2")));
        mvc.perform(post(RECORDS + "/induce").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{\"interventionIds\":[\"1\",\"2\"]}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.scenarioCode").value("FAKE_INVESTMENT"))
                .andExpect(jsonPath("$.data.ruleText").value("· 第一条\n· 第二条"))
                .andExpect(jsonPath("$.data.sourceInterventionIds[1]").value("2"));
    }

    @Test
    void mutationRequiresCsrfAndUnknownQueryIsRejected() throws Exception {
        mvc.perform(post(RULES).with(authentication(auth(UserRole.ADMIN, "7")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(get(RULES + "?x=1").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.fieldErrors[0].code").value("UNKNOWN_FIELD"));
        mvc.perform(get(RECORDS + "?status=PENDING").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void aNonNumericRuleIdIsANotFound() throws Exception {
        mvc.perform(get(RULES + "/abc/sources").with(authentication(auth(UserRole.ADMIN, "7"))))
                .andExpect(status().isNotFound());
        mvc.perform(put(RULES + "/abc").with(authentication(auth(UserRole.ADMIN, "7"))).with(csrf().asHeader())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
