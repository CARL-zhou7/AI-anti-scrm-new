package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.simulation.domain.SimulationSession;
import com.aiscam.antiscam.simulation.rules.SimulationRuleset;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationRulesetTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private static SimulationSession session(String rulesetVersion) {
        return new SimulationSession(1, "request", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"" + rulesetVersion + "\"}", NOW, NOW, "{}", 1000);
    }

    @Test
    void v35RunsFifteenRounds() {
        var session = session(SimulationRuleset.RULESET_V35);
        assertTrue(SimulationRuleset.isV35(session));
        assertEquals(15, SimulationRuleset.maxRounds(session));
        assertFalse(SimulationRuleset.isFinalRound(session, 14));
        assertTrue(SimulationRuleset.isFinalRound(session, 15));
        assertFalse(SimulationRuleset.isFinalRound(session, 16));
    }

    @Test
    void v34KeepsTwentyRounds() {
        var session = session(SimulationRuleset.RULESET_V34);
        assertFalse(SimulationRuleset.isV35(session));
        assertEquals(20, SimulationRuleset.maxRounds(session));
        assertFalse(SimulationRuleset.isFinalRound(session, 15));
        assertTrue(SimulationRuleset.isFinalRound(session, 20));
    }

    @Test
    void unreadableOrMissingRuntimeSnapshotIsNeverV35() {
        var legacy = new SimulationSession(1, "old", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4, "{}", NOW, NOW, "{}", 1000);
        assertFalse(SimulationRuleset.isV35(legacy));
        assertEquals(20, SimulationRuleset.maxRounds(legacy));
        var nullRuntime = new SimulationSession(1, "null", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4, null, NOW, NOW, "{}", 1000);
        assertFalse(SimulationRuleset.isV35(nullRuntime));
    }
}
