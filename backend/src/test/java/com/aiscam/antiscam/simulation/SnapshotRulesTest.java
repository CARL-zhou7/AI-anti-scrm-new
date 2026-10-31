package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotRulesTest {
    @Test void newRulesAndBudgetAreFrozenWithoutChangingLegacyDeadline() {
        var now = Instant.parse("2026-10-26T00:00:00Z");
        var legacy = new SimulationSession(1,"old",1,1,1,"{}","{}","{}",1,2,3,4,"{}",now,now,"{}",1000);
        var current = new SimulationSession(1,"new",1,1,1,"{}","{}","{}",1,2,3,4,
                "{\"trust_config\":{\"trust_ruleset_version\":\"trust-v2\",\"threshold_small\":70}}",now,now,"{}",1000);
        assertFalse(ConfigurationSnapshots.trustV2(legacy));
        assertTrue(ConfigurationSnapshots.trustV2(current));
        current.initializeActiveDuration(1_800_000);
        assertEquals(now.plusSeconds(600),legacy.getDeadlineAt());
        assertEquals(now.plusSeconds(1800),current.getDeadlineAt());
        current.enterRound(1);
        assertThrows(IllegalArgumentException.class,()->current.initializeActiveDuration(600_000));
    }
    @Test
    void frozenSceneRulesReachOnlyTheScammerContextInSnapshotOrder() {
        var session = new SimulationSession(1, "rules", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"PRD-V3.5\"}", Instant.parse("2026-10-28T00:00:00Z"),
                Instant.parse("2026-10-28T00:00:00Z"), "{}", 10000);
        var json = snapshots.write(java.util.List.of(
                new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot("7", "称呼", "始终使用既定亲属称谓", 2),
                new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot("3", "事实", "材料金额必须前后一致", 1)));
        session.freezeSceneRules(json);

        assertEquals(2, snapshots.sceneRuleCount(json));
        String text = snapshots.sceneRuleText(session.getSceneRuleSnapshot());
        assertTrue(text.indexOf("【称呼】") < text.indexOf("【事实】"), "the frozen order is kept verbatim");
        assertTrue(text.contains("始终使用既定亲属称谓"));
        assertTrue(snapshots.sceneRuleText(null).isEmpty());

        var scene = new com.aiscam.antiscam.agent.AgentModels.AgentScene(
                com.aiscam.antiscam.content.domain.ScenarioCode.FAKE_INVESTMENT, "名称", "简介", "骗子画像", "受害者画像",
                "陈启明", "scammer{{scene_rules}}", "victim", "narrator", "公开背景", true, null, true, false, true, text);
        var scammer = com.aiscam.antiscam.agent.AgentContextAssembler.scammer(scene, java.util.List.of(), 1, 900);
        assertTrue(scammer.get("scene_rules").contains("【称呼】"));
        assertTrue(scammer.get("scene_rules").contains("材料金额必须前后一致"));
        assertFalse(com.aiscam.antiscam.agent.AgentContextAssembler.victim(scene, java.util.List.of(),
                new com.aiscam.antiscam.agent.AgentModels.DialogueLine(1,
                        com.aiscam.antiscam.simulation.domain.SpeakerRole.SCAMMER, "台词", null, null),
                1, 900, false).containsKey("scene_rules"), "the victim never receives the scammer playbook");
        assertFalse(com.aiscam.antiscam.agent.AgentContextAssembler.narrator(scene, java.util.List.of(), 1,
                com.aiscam.antiscam.agent.AgentModels.PriorFacts.none()).containsKey("scene_rules"));

        var empty = new com.aiscam.antiscam.agent.AgentModels.AgentScene(
                com.aiscam.antiscam.content.domain.ScenarioCode.FAKE_INVESTMENT, "名称", "简介", "骗子画像", "受害者画像",
                "陈启明", "scammer{{scene_rules}}", "victim", "narrator", "公开背景", true, null, true, false, true, "");
        assertEquals("（本场没有额外的场景规则）", com.aiscam.antiscam.agent.AgentContextAssembler
                .scammer(empty, java.util.List.of(), 1, 900).get("scene_rules"));

        // A session pinned to the v18 scammer prompt has no placeholder: the variable must not be added at all,
        // because the renderer rejects unused variables.
        var legacy = new com.aiscam.antiscam.agent.AgentModels.AgentScene(
                com.aiscam.antiscam.content.domain.ScenarioCode.FAKE_INVESTMENT, "名称", "简介", "骗子画像", "受害者画像",
                "陈启明", "scammer", "victim", "narrator", "公开背景", true, null, true, false, true, text);
        assertFalse(com.aiscam.antiscam.agent.AgentContextAssembler.scammer(legacy, java.util.List.of(), 1, 900)
                .containsKey("scene_rules"));
    }

    @Test
    void editingARuleLaterNeverChangesAFrozenSnapshot() {
        var rule = new SceneRule("FAKE_INVESTMENT", "称呼与事实一致", "始终使用既定亲属称谓", 7);
        ReflectionTestUtils.setField(rule, "id", 9L);
        var session = new SimulationSession(1, "frozen", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"PRD-V3.5\"}", Instant.parse("2026-10-28T00:00:00Z"),
                Instant.parse("2026-10-28T00:00:00Z"), "{}", 10000);
        session.freezeSceneRules(snapshots.write(java.util.List.of(
                new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot(rule.getId().toString(),
                        rule.getTitle(), rule.getRuleText(), rule.getVersionNo()))));

        rule.update("收束节奏", "第13轮必须收束", SceneRuleStatus.DISABLED);

        assertTrue(snapshots.sceneRuleText(session.getSceneRuleSnapshot()).contains("始终使用既定亲属称谓"));
        assertFalse(snapshots.sceneRuleText(session.getSceneRuleSnapshot()).contains("第13轮必须收束"));
        assertEquals(1, snapshots.sceneRuleCount(session.getSceneRuleSnapshot()));
    }

    @Test
    void twoConflictingEnabledRulesAreBothFrozenWithoutPausingTheRun() {
        var a = new SceneRule("FAKE_INVESTMENT", "先提收益", "每轮都强调收益", 7);
        var b = new SceneRule("FAKE_INVESTMENT", "先埋伏笔", "每轮都先铺垫再提收益", 7);
        var session = new SimulationSession(1, "conflict", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"PRD-V3.5\"}", Instant.parse("2026-10-28T00:00:00Z"),
                Instant.parse("2026-10-28T00:00:00Z"), "{}", 10000);
        session.freezeSceneRules(snapshots.write(java.util.List.of(
                new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot("8", b.getTitle(), b.getRuleText(), 1),
                new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot("5", a.getTitle(), a.getRuleText(), 1))));

        assertEquals(2, snapshots.sceneRuleCount(session.getSceneRuleSnapshot()),
                "conflicts stay a manual concern; the runtime injects every enabled rule");
        assertEquals(com.aiscam.antiscam.simulation.domain.SimulationStatus.RUNNING, session.getStatus());
        assertNull(session.getOutcomeCode());
        assertNull(session.getInterruptionCode());
    }

    private final ConfigurationSnapshots snapshots = new ConfigurationSnapshots(new ObjectMapper());

    @Test
    void freezesAllFactsAndScenariosAndReadsLegacyWithoutInventingMoney() {
        var profiles = Arrays.stream(ScenarioCode.values()).map(c -> new VictimScenarioProfileValues(c.name(),
                "需求", "经历", "依据", "核实", "边界", "停止", "昨天", "咨询", "共同经历", "背景", "信任", 50, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, 200)).toList();
        var p = new VictimProfile(new VictimProfileValues("测试", 30, "女", "身份", "描述", "家庭", "收入",
                2000, 50000, 10000, 40000, 0, "预留安排", "决策特点", "家中", "查看消息", Level.MEDIUM,
                Level.LOW, "性格", "avatars/" + "a".repeat(64) + ".png", "avatars/" + "b".repeat(64) + ".png", profiles));
        ReflectionTestUtils.setField(p, "id", 1L);
        String json = snapshots.victim(p, ScenarioCode.FAKE_INVESTMENT);
        var v = snapshots.read(json);
        assertEquals(3, v.path("snapshot_schema_version").asInt());
        assertEquals("FAKE_INVESTMENT", v.path("selected_scenario_code").asText());
        assertEquals(3, v.path("victim_scenario_profiles").size());
        assertEquals("家庭", v.path("family_status").asText());
        assertEquals(10000, v.path("available_funds_yuan").asInt());
        assertEquals("昨天20:00", v.path("context_time").asText());
        String publicFacts = snapshots.victimPublicText(json);
        assertTrue(publicFacts.contains("家庭"));
        for (String privateFact : java.util.List.of("available_funds", "reserved_funds", "10000", "40000", "initial_trust_mode", "general_decision_traits")) {
            assertFalse(publicFacts.contains(privateFact));
        }
        String scenario = "{\"id\":\"1\",\"scenario_code\":\"FAKE_INVESTMENT\"}";
        String scammer = json.replace("\"anti_fraud_awareness\"", "\"fraud_experience\"")
                .replace("\"risk_tendency\"", "\"speech_skill\"").replace("\"personality_traits\":\"性格\"", "\"manipulation_skill\":\"LOW\"");
        var view = snapshots.toConfigurationView(scenario, scammer, json).victim();
        assertEquals(3, view.profileSchemaVersion());
        assertEquals(profiles.stream().map(com.aiscam.antiscam.content.web.dto.VictimScenarioProfileView::from).toList(), view.scenarioProfiles());
        assertEquals(10000, view.availableFundsYuan());
        var old = (com.fasterxml.jackson.databind.node.ObjectNode) v.deepCopy();
        old.remove("snapshot_schema_version");
        old.remove("victim_scenario_profiles");
        old.remove("available_funds_yuan");
        var legacy = snapshots.toConfigurationView(scenario, scammer, old.toString()).victim();
        assertEquals(1, legacy.profileSchemaVersion());
        assertNull(legacy.availableFundsYuan());
        assertTrue(legacy.scenarioProfiles().isEmpty());
        ReflectionTestUtils.setField(p, "availableFundsYuan", 999);
        assertEquals(10000, snapshots.read(json).path("available_funds_yuan").asInt());
    }

    @Test
    void startsWithAvailableFundsAndNoTransactions() {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        var s = new SimulationSession(1, "request", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{}", now, now, "{}", 10000);
        assertEquals(2, s.getSnapshotSchemaVersion());
        assertEquals(new FundsSummary(10000, 10000, 0, 0, 0, 0, 0), s.getFundsSummary());
    }

    @Test
    void legacyFundsKeepTotalAndAllocateGeneralBeforeEmergency() {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        var s = new SimulationSession(1, "request", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{}", now, now, "{}", 10000);
        s.applyFunds(new FundsSummary(10000, 10100, 0, 0, 0, 0, 100));
        assertEquals(100, s.getGeneralReserveReleasedYuan());
        assertEquals(0, s.getEmergencyReserveReleasedYuan());
        s.initializeReserveTotals(7500, 1500);
        s.applyFunds(new FundsSummary(10000, 18000, 0, 0, 0, 0, 8000));
        assertEquals(7500, s.getGeneralReserveReleasedYuan());
        assertEquals(500, s.getEmergencyReserveReleasedYuan());
        assertEquals(8000, s.getFundsSummary().reserveReleasedYuan());
        assertNull(s.getInitialTrustScore());
    }
}
