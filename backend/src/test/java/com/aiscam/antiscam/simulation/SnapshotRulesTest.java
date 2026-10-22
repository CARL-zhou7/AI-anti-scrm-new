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
    private final ConfigurationSnapshots snapshots = new ConfigurationSnapshots(new ObjectMapper());

    @Test
    void freezesAllFactsAndScenariosAndReadsLegacyWithoutInventingMoney() {
        var profiles = Arrays.stream(ScenarioCode.values()).map(c -> new VictimScenarioProfileValues(c.name(),
                "需求", "经历", "依据", "核实", "边界", "停止", "昨天", "咨询", "共同经历", "背景", "信任")).toList();
        var p = new VictimProfile(new VictimProfileValues("测试", 30, "女", "身份", "描述", "家庭", "收入",
                2000, 50000, 10000, 40000, "预留安排", "决策特点", "家中", "查看消息", Level.MEDIUM,
                Level.LOW, "性格", "avatars/" + "a".repeat(64) + ".png", "avatars/" + "b".repeat(64) + ".png", profiles));
        ReflectionTestUtils.setField(p, "id", 1L);
        String json = snapshots.victim(p, ScenarioCode.FAKE_INVESTMENT);
        var v = snapshots.read(json);
        assertEquals(2, v.path("snapshot_schema_version").asInt());
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
        assertEquals(2, view.profileSchemaVersion());
        assertEquals(profiles, view.scenarioProfiles());
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
}
