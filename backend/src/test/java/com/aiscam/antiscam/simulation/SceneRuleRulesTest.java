package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SceneRuleService;
import com.aiscam.antiscam.simulation.web.dto.SceneRuleViews.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scenario knowledge base rules (Phase 3, Task 12). Only the administrator's own adopted interventions may become
 * sources, one source belongs to exactly one rule, merging several sources into one rule is a single transaction, and
 * a rule failure never touches a running session.
 */
class SceneRuleRulesTest {
    private static final long ADMIN = 7L;

    private final SceneRuleRepository rules = mock(SceneRuleRepository.class);
    private final SceneRuleSourceRepository sources = mock(SceneRuleSourceRepository.class);
    private final SimulationInterventionRepository interventions = mock(SimulationInterventionRepository.class);
    private final com.aiscam.antiscam.agent.LlmClient llm = mock(com.aiscam.antiscam.agent.LlmClient.class);
    private final SceneRuleService service = new SceneRuleService(rules, sources, interventions, llm,
            new com.aiscam.antiscam.config.SimulationProperties(), new com.fasterxml.jackson.databind.ObjectMapper());

    private static SimulationIntervention adopted(long id, String scene, long admin, String finalContent) {
        var record = new SimulationIntervention(100 + id, 200 + id, (int) id, 300 + id, scene, admin,
                "模型原台词" + id, "发送材料", finalContent, null, null, InterventionMode.ADMIN_TEST);
        ReflectionTestUtils.setField(record, "id", id);
        record.link(400 + id);
        return record;
    }

    private static SimulationIntervention pending(long id, String scene, long admin) {
        var record = new SimulationIntervention(100 + id, 200 + id, (int) id, 300 + id, scene, admin,
                "模型原台词" + id, null, null, null, null, InterventionMode.ADMIN_TEST);
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }

    private static SceneRule rule(long id, String scene, String title, long owner) {
        var rule = new SceneRule(scene, title, "规则正文", owner);
        ReflectionTestUtils.setField(rule, "id", id);
        return rule;
    }

    private void ids(SceneRule created, long id) { ReflectionTestUtils.setField(created, "id", id); }

    @Test
    void threeWaitingSourcesBecomeOneEnabledRuleAndAllBecomeLinked() {
        var a = adopted(1, "FAKE_INVESTMENT", ADMIN, "第一次改写台词");
        var b = adopted(2, "FAKE_INVESTMENT", ADMIN, "第二次改写台词");
        var c = adopted(3, "FAKE_INVESTMENT", ADMIN, "第三次改写台词");
        when(interventions.lockByIdInAndAdminUserId(List.of(1L, 2L, 3L), ADMIN)).thenReturn(List.of(a, b, c));
        when(rules.saveAndFlush(any(SceneRule.class))).thenAnswer(invocation -> {
            SceneRule created = invocation.getArgument(0);
            ids(created, 42);
            return created;
        });
        when(sources.countByRuleId(42)).thenReturn(3L);

        SceneRuleView view = service.create(ADMIN, new SceneRuleWrite("FAKE_INVESTMENT", "称呼与事实一致",
                "始终沿用既定称谓，不虚构未公开姓名", List.of("1", "2", "3")));

        assertEquals("42", view.ruleId());
        assertEquals("ENABLED", view.status());
        assertEquals(1, view.version());
        assertEquals(3, view.sourceCount());
        assertEquals(List.of(InterventionStatus.LINKED, InterventionStatus.LINKED, InterventionStatus.LINKED),
                List.of(a.getStatus(), b.getStatus(), c.getStatus()));
        verify(sources, times(3)).save(any(SceneRuleSource.class));
        verify(sources, never()).delete(any());
    }

    @Test
    void aSourceThatIsAlreadyLinkedCannotEnterSecondRule() {
        var a = adopted(1, "FAKE_INVESTMENT", ADMIN, "第一次改写台词");
        a.linkToRule();
        when(interventions.lockByIdInAndAdminUserId(List.of(1L), ADMIN)).thenReturn(List.of(a));

        var conflict = assertThrows(ApiException.class, () -> service.create(ADMIN,
                new SceneRuleWrite("FAKE_INVESTMENT", "另一条规则", "正文", List.of("1"))));

        assertEquals(409, conflict.status);
        assertEquals("SOURCE_ALREADY_LINKED", conflict.code);
        verify(rules, never()).saveAndFlush(any(SceneRule.class));
        assertEquals(InterventionStatus.LINKED, a.getStatus());
    }

    @Test
    void theSameTitleInOneSceneIsAConflict() {
        when(rules.existsByScenarioCodeAndTitle("FAKE_INVESTMENT", "称呼与事实一致")).thenReturn(true);

        var conflict = assertThrows(ApiException.class, () -> service.create(ADMIN,
                new SceneRuleWrite("FAKE_INVESTMENT", "称呼与事实一致", "正文", List.of("1"))));

        assertEquals(409, conflict.status);
        assertEquals("RULE_TITLE_CONFLICT", conflict.code);
        verify(interventions, never()).lockByIdInAndAdminUserId(anyList(), anyLong());
        verify(rules, never()).saveAndFlush(any(SceneRule.class));
    }

    @Test
    void sourcesOfDifferentScenesOrOfAnotherAdministratorAreRejectedWithoutChangingAnything() {
        var mine = adopted(1, "FAKE_INVESTMENT", ADMIN, "投资改写");
        var otherScene = adopted(2, "FAKE_LOAN", ADMIN, "借款改写");
        when(interventions.lockByIdInAndAdminUserId(List.of(1L, 2L), ADMIN)).thenReturn(List.of(mine, otherScene));

        var mixed = assertThrows(ApiException.class, () -> service.create(ADMIN,
                new SceneRuleWrite("FAKE_INVESTMENT", "称呼与事实一致", "正文", List.of("1", "2"))));

        assertEquals(400, mixed.status);
        assertEquals("VALIDATION_FAILED", mixed.code);
        assertEquals(InterventionStatus.ADOPTED, mine.getStatus(), "a rejected merge keeps the source waiting");
        assertEquals(InterventionStatus.ADOPTED, otherScene.getStatus());
        verify(rules, never()).saveAndFlush(any(SceneRule.class));

        // The lock query is scoped by administrator, so a foreign id simply cannot be returned.
        when(interventions.lockByIdInAndAdminUserId(List.of(9L), ADMIN)).thenReturn(List.of());
        var foreign = assertThrows(ApiException.class, () -> service.create(ADMIN,
                new SceneRuleWrite("FAKE_INVESTMENT", "称呼与事实一致", "正文", List.of("9"))));
        assertEquals(404, foreign.status);
        verify(sources, never()).save(any(SceneRuleSource.class));
    }

    @Test
    void aPendingSourceIsRejectedBecauseOnlyAdoptedRecordsMayEnterTheKnowledgeBase() {
        var waiting = pending(4, "FAKE_INVESTMENT", ADMIN);
        when(interventions.lockByIdInAndAdminUserId(List.of(4L), ADMIN)).thenReturn(List.of(waiting));

        var conflict = assertThrows(ApiException.class, () -> service.create(ADMIN,
                new SceneRuleWrite("FAKE_INVESTMENT", "称呼与事实一致", "正文", List.of("4"))));

        assertEquals(409, conflict.status);
        assertEquals("SOURCE_ALREADY_LINKED", conflict.code);
        assertEquals(InterventionStatus.PENDING, waiting.getStatus());
        verify(rules, never()).saveAndFlush(any(SceneRule.class));
    }

    @Test
    void mergingLocksTheSourcesSoTwoAdministratorsCannotMergeTheSameRecord() {
        var a = adopted(1, "FAKE_INVESTMENT", ADMIN, "改写台词");
        when(interventions.lockByIdInAndAdminUserId(List.of(1L), ADMIN)).thenReturn(List.of(a));
        when(rules.saveAndFlush(any(SceneRule.class))).thenAnswer(invocation -> {
            SceneRule created = invocation.getArgument(0);
            ids(created, 50);
            return created;
        });
        when(sources.countByRuleId(50)).thenReturn(1L);

        service.create(ADMIN, new SceneRuleWrite("FAKE_INVESTMENT", "称呼与事实一致", "正文", List.of("1")));

        verify(interventions).lockByIdInAndAdminUserId(List.of(1L), ADMIN);
        verify(interventions, never()).findByIdInAndAdminUserId(anyList(), anyLong());
        assertEquals(InterventionStatus.LINKED, a.getStatus());
    }

    @Test
    void aFailureWhileLinkingSourcesPropagatesSoTheWholeMergeRollsBack() throws Exception {
        var a = adopted(1, "FAKE_INVESTMENT", ADMIN, "第一次改写台词");
        var b = adopted(2, "FAKE_INVESTMENT", ADMIN, "第二次改写台词");
        when(interventions.lockByIdInAndAdminUserId(List.of(1L, 2L), ADMIN)).thenReturn(List.of(a, b));
        when(rules.saveAndFlush(any(SceneRule.class))).thenAnswer(invocation -> {
            SceneRule created = invocation.getArgument(0);
            ids(created, 60);
            return created;
        });
        when(sources.save(any(SceneRuleSource.class))).thenThrow(new IllegalStateException("来源关联失败"));

        assertThrows(IllegalStateException.class, () -> service.create(ADMIN,
                new SceneRuleWrite("FAKE_INVESTMENT", "称呼与事实一致", "正文", List.of("1", "2"))),
                "the merge must surface the failure instead of committing half a rule");

        // Rollback itself belongs to Spring: prove the write path is a read-write transaction with no swallowed error.
        var create = SceneRuleService.class.getMethod("create", long.class, SceneRuleWrite.class);
        var transaction = create.getAnnotation(Transactional.class);
        assertNotNull(transaction, "create must run inside one transaction");
        assertFalse(transaction.readOnly());
        verify(interventions, never()).flush();
    }

    @Test
    void induceIsReadOnlyAndKeepsTheSourcesWaiting() {
        var a = adopted(1, "FAKE_INVESTMENT", ADMIN, "第一次改写台词");
        var b = adopted(2, "FAKE_INVESTMENT", ADMIN, "第二次改写台词");
        when(interventions.findByIdInAndAdminUserId(List.of(1L, 2L), ADMIN)).thenReturn(List.of(a, b));

        a.setPreviousContext("受害者：先用模拟盘，不投入真金白银");
        when(llm.completeJson(any())).thenReturn("{\"title\":\"模拟盘观察阶段的回应\",\"condition\":\"受害者只愿继续观察模拟盘时\",\"guidance\":\"沿用模拟资金展示操作，区分模拟收益和真实到账\"}");
        RuleDraft draft = service.induce(ADMIN, List.of("1", "2"));

        assertEquals("FAKE_INVESTMENT", draft.scenarioCode());
        assertEquals(List.of("1", "2"), draft.sourceInterventionIds());
        assertEquals("模拟盘观察阶段的回应", draft.title());
        assertTrue(draft.ruleText().contains("适用情况：受害者只愿继续观察模拟盘时"));
        assertTrue(draft.ruleText().contains("话术指导：沿用模拟资金"));
        assertFalse(draft.ruleText().contains("第一次改写台词"));
        var request = org.mockito.ArgumentCaptor.forClass(com.aiscam.antiscam.agent.LlmClient.LlmRequest.class);
        verify(llm).completeJson(request.capture());
        assertTrue(request.getValue().prompt().contains("先用模拟盘，不投入真金白银"));
        assertTrue(request.getValue().prompt().contains("第一次改写台词"));
        assertTrue(request.getValue().prompt().contains("第二次改写台词"));
        assertEquals(InterventionStatus.ADOPTED, a.getStatus());
        assertEquals(InterventionStatus.ADOPTED, b.getStatus());
        verify(rules, never()).saveAndFlush(any(SceneRule.class));
        verify(sources, never()).save(any(SceneRuleSource.class));
    }

    @Test
    void invalidInductionDoesNotConsumeSourcesOrReturnAPastedFallback() {
        var a = adopted(1, "FAKE_INVESTMENT", ADMIN, "模拟盘500元");
        when(interventions.findByIdInAndAdminUserId(List.of(1L), ADMIN)).thenReturn(List.of(a));
        for (String raw : List.of("not json", "{\"title\":\"规则\",\"condition\":\"观察时\",\"guidance\":\"追加500元\"}")) {
            when(llm.completeJson(any())).thenReturn(raw);
            assertEquals(503, assertThrows(ApiException.class, () -> service.induce(ADMIN, List.of("1"))).status);
            assertEquals(InterventionStatus.ADOPTED, a.getStatus());
        }
        verifyNoInteractions(rules, sources);
    }

    @Test
    void editingAndDisablingBumpTheVersionAndAStaleVersionIsAConflict() {
        var target = rule(42, "FAKE_INVESTMENT", "称呼与事实一致", ADMIN);
        when(rules.lockById(42L)).thenReturn(java.util.Optional.of(target));
        when(sources.countByRuleId(42)).thenReturn(2L);

        var stale = assertThrows(ApiException.class, () -> service.update(ADMIN, 42,
                new SceneRuleUpdate("新标题", "新正文", "DISABLED", 2)));
        assertEquals(409, stale.status);
        assertEquals("VERSION_CONFLICT", stale.code);
        assertEquals(1, target.getVersionNo());
        assertEquals(SceneRuleStatus.ENABLED, target.getStatus());

        SceneRuleView disabled = service.update(ADMIN, 42, new SceneRuleUpdate("新标题", "新正文", "DISABLED", 1));
        assertEquals(2, disabled.version());
        assertEquals("DISABLED", disabled.status());
        assertEquals("新标题", disabled.title());
        assertEquals(2L, disabled.sourceCount());
    }

    @Test
    void editingAnotherAdministratorsRuleIsANotFound() {
        var foreign = rule(42, "FAKE_INVESTMENT", "称呼与事实一致", 8L);
        when(rules.lockById(42L)).thenReturn(java.util.Optional.of(foreign));

        var missing = assertThrows(ApiException.class, () -> service.update(ADMIN, 42,
                new SceneRuleUpdate("新标题", null, null, null)));

        assertEquals(404, missing.status);
    }

    @Test
    void sourcesOfOneRuleOnlyEverReturnTheCallersOwnRecords() {
        var mine = adopted(1, "FAKE_INVESTMENT", ADMIN, "我的改写");
        var foreign = adopted(2, "FAKE_INVESTMENT", 8L, "别人的改写");
        when(rules.findById(42L)).thenReturn(java.util.Optional.of(rule(42, "FAKE_INVESTMENT", "规则", ADMIN)));
        when(sources.findByRuleIdOrderByInterventionIdAsc(42)).thenReturn(List.of(
                new SceneRuleSource(42, 1, ADMIN), new SceneRuleSource(42, 2, 8L)));
        when(interventions.findById(1L)).thenReturn(java.util.Optional.of(mine));
        when(interventions.findById(2L)).thenReturn(java.util.Optional.of(foreign));

        var records = service.sources(ADMIN, 42);

        assertEquals(1, records.size());
        assertEquals("1", records.getFirst().interventionId());
        assertEquals("42", records.getFirst().ruleId());
    }

    @Test
    void sourcesOfAnotherAdministratorsRuleAreNotReadable() {
        when(rules.findById(42L)).thenReturn(java.util.Optional.of(rule(42, "FAKE_INVESTMENT", "规则", 8L)));

        var missing = assertThrows(ApiException.class, () -> service.sources(ADMIN, 42));

        assertEquals(404, missing.status);
        verify(sources, never()).findByRuleIdOrderByInterventionIdAsc(anyLong());
    }

    @Test
    void anUnreadableStatusOrSourceIdIsAFieldError() {
        var target = rule(42, "FAKE_INVESTMENT", "称呼与事实一致", ADMIN);
        when(rules.lockById(42L)).thenReturn(java.util.Optional.of(target));
        var badStatus = assertThrows(ApiException.class, () -> service.update(ADMIN, 42,
                new SceneRuleUpdate(null, null, "ARCHIVED", null)));
        assertEquals(400, badStatus.status, "an unknown status is a field error, not a missing rule");
        assertEquals(1, target.getVersionNo());
        var badId = assertThrows(ApiException.class, () -> service.induce(ADMIN, List.of("abc")));
        assertEquals(400, badId.status);
        var empty = assertThrows(ApiException.class, () -> service.induce(ADMIN, List.of()));
        assertEquals(400, empty.status);
    }
}
