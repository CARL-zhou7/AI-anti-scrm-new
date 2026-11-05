package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.agent.LlmClient;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SceneRuleService;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot;
import com.aiscam.antiscam.simulation.web.dto.SceneRuleViews.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VictimSceneRuleManagementTest {
    private final SceneRuleRepository rules = mock(SceneRuleRepository.class);
    private final SceneRuleSourceRepository sources = mock(SceneRuleSourceRepository.class);
    private final SimulationInterventionRepository interventions = mock(SimulationInterventionRepository.class);
    private final SceneRuleService service = new SceneRuleService(rules, sources, interventions, mock(LlmClient.class),
            new SimulationProperties(), new ObjectMapper());

    private static final String TEXT = "适用情况：同一请求反复遭拒。\n行为指导：第三次符合条件时配合。";

    @Test void victimRulesCanBeCreatedWithoutInterventionSourcesAndDoNotConsumeScammerSources() {
        when(rules.saveAndFlush(any())).thenAnswer(call -> {
            SceneRule r = call.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 42L);
            return r;
        });
        var result = service.create(7, new SceneRuleWrite("FAKE_INVESTMENT", "重复拒绝后配合", TEXT,
                List.of(), "VICTIM"));
        assertEquals("VICTIM", result.targetRole());
        assertEquals("ENABLED", result.status());
        assertEquals(0, result.sourceCount());
        verify(rules).existsByScenarioCodeAndTargetRoleAndTitle("FAKE_INVESTMENT", SpeakerRole.VICTIM, "重复拒绝后配合");
        verify(interventions, never()).lockByIdInAndAdminUserId(anyList(), anyLong());
        verify(sources, never()).save(any());
    }

    @Test void sameVictimTitleConflictsWithinItsRoleButScammerAndVictimUseSeparateNamespaces() {
        when(rules.existsByScenarioCodeAndTargetRoleAndTitle("TASK_REBATE", SpeakerRole.VICTIM, "重复拒绝后配合"))
                .thenReturn(true);
        assertEquals(409, assertThrows(ApiException.class, () -> service.create(7,
                new SceneRuleWrite("TASK_REBATE", "重复拒绝后配合", TEXT, List.of(), "VICTIM"))).status);
        verify(rules, never()).existsByScenarioCodeAndTitle(anyString(), anyString());
        verify(rules, never()).saveAndFlush(any());
    }

    @Test void victimsCannotLinkScammerSources() {
        assertEquals(400, assertThrows(ApiException.class, () -> service.create(7,
                new SceneRuleWrite("TASK_REBATE", "标题", TEXT, List.of("1"), "VICTIM"))).status);
        verify(rules, never()).saveAndFlush(any());
    }

    @Test void scammersCanBeCreatedDirectlyWithEmptyOrOmittedSources() {
        when(rules.saveAndFlush(any())).thenAnswer(call -> {
            SceneRule rule = call.getArgument(0);
            ReflectionTestUtils.setField(rule, "id", 43L);
            return rule;
        });
        for (var request : List.of(new SceneRuleWrite("TASK_REBATE", "直接规则", TEXT, List.of(), "SCAMMER"),
                new SceneRuleWrite("TASK_REBATE", "直接规则", TEXT, null))) {
            var result = service.create(7, request);
            assertEquals("SCAMMER", result.targetRole());
            assertEquals(0, result.sourceCount());
        }
        verify(interventions, never()).lockByIdInAndAdminUserId(anyList(), anyLong());
        verify(sources, never()).save(any());
    }

    @Test void invalidSceneRoleAndOversizedTextAreRejectedAtTheServiceBoundary() {
        for (var request : List.of(
                new SceneRuleWrite("UNKNOWN", "标题", TEXT, List.of(), "VICTIM"),
                new SceneRuleWrite("TASK_REBATE", "标题", TEXT, List.of(), "REPORT"),
                new SceneRuleWrite("TASK_REBATE", "长".repeat(129), TEXT, List.of(), "VICTIM"),
                new SceneRuleWrite("TASK_REBATE", "标题", "长".repeat(4001), List.of(), "VICTIM"))) {
            assertEquals(400, assertThrows(ApiException.class, () -> service.create(7, request)).status);
        }
        verify(rules, never()).saveAndFlush(any());
    }

    @Test void victimEditingKeepsOwnershipVersionChecksAndItsTargetRole() {
        var r = new SceneRule("TASK_REBATE", "标题", TEXT, 7, SpeakerRole.VICTIM);
        ReflectionTestUtils.setField(r, "id", 42L);
        when(rules.lockById(42L)).thenReturn(Optional.of(r));
        assertEquals(404, assertThrows(ApiException.class, () -> service.update(8, 42,
                new SceneRuleUpdate("新标题", TEXT, "DISABLED", 1))).status);
        assertEquals(409, assertThrows(ApiException.class, () -> service.update(7, 42,
                new SceneRuleUpdate("新标题", TEXT, "DISABLED", 2))).status);
        var updated = service.update(7, 42, new SceneRuleUpdate("新标题", TEXT, "DISABLED", 1));
        assertEquals("VICTIM", updated.targetRole());
        assertEquals(2, updated.version());
        assertEquals("DISABLED", updated.status());
    }

    @Test void roleFilteredSnapshotsKeepLegacyRulesOnTheScammerSideAndFrozenTextIsImmutable() {
        var snapshots = new ConfigurationSnapshots(new ObjectMapper());
        String json = snapshots.write(List.of(
                new SceneRuleSnapshot("1", "骗子规则", "保持称谓", 1),
                new SceneRuleSnapshot("2", "受害者规则", TEXT, 3, "VICTIM")));
        assertTrue(snapshots.sceneRuleText(json).contains("保持称谓"));
        assertFalse(snapshots.sceneRuleText(json).contains("第三次"));
        assertTrue(snapshots.sceneRuleText(json, "VICTIM").contains("第三次"));
        assertFalse(snapshots.sceneRuleText(json, "VICTIM").contains("保持称谓"));
        String legacy = "[{\"ruleId\":\"9\",\"title\":\"旧规则\",\"ruleText\":\"旧正文\",\"version\":1}]";
        assertTrue(snapshots.sceneRuleText(legacy).contains("旧正文"));
        assertEquals("", snapshots.sceneRuleText(legacy, "VICTIM"));
        var edited = new SceneRule("TASK_REBATE", "受害者规则", TEXT, 7, SpeakerRole.VICTIM);
        edited.update(null, "改变后的规则", SceneRuleStatus.DISABLED);
        assertFalse(snapshots.sceneRuleText(json, "VICTIM").contains("改变后的规则"));
    }
}
