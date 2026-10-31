package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.SimulationSession;
import com.aiscam.antiscam.simulation.rules.OutcomeRules;
import com.aiscam.antiscam.simulation.rules.SimulationRuleset;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V1.4 has no business deadline, no early verdict and no funds/exit termination. This covers the rule and session
 * layer; the runs that used to stop on FUNDS_EXHAUSTED/CHARACTER_EXIT are gated in the orchestrator by
 * {@code trustV3}, and old PRD-V3.4 sessions keep both paths.
 */
class NoEarlyEndV35Test {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private static SimulationSession v35() {
        return new SimulationSession(1, "request", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"PRD-V3.5\",\"trust_config\":{\"trust_ruleset_version\":\"trust-v3\"}}",
                NOW, NOW, "{}", 1000);
    }

    private static SimulationSession v34() {
        return new SimulationSession(1, "legacy", 1, 1, 1, "{}", "{}", "{}", 1, 2, 3, 4,
                "{\"ruleset_version\":\"PRD-V3.4\",\"trust_config\":{\"trust_ruleset_version\":\"trust-v2\"}}",
                NOW, NOW, "{}", 1000);
    }

    @Test
    void v35DoesNotDecideBeforeTheFinalRound() {
        var session = v35();
        for (int round = 1; round <= 14; round++) {
            assertNull(OutcomeRules.decideV35(round, 100, 10_000), "round " + round + " must not end");
            assertFalse(SimulationRuleset.isFinalRound(session, round));
        }
        assertTrue(SimulationRuleset.isFinalRound(session, 15));
        assertEquals(15, SimulationRuleset.maxRounds(session));
    }

    @Test
    void v35FinishStoresWinnerFinalScoreAndNoLimit() {
        var session = v35();
        session.enterRound(15);
        session.applyTrustScore(82);
        session.finishV35(OutcomeCode.SCAMMER_WIN, "第15轮结束判定", 82, NOW);
        assertEquals("SCAMMER", session.getWinner());
        assertEquals(82, session.getFinalTrustScore());
        assertNull(session.getLimitReason());
        assertEquals(OutcomeCode.SCAMMER_WIN, session.getOutcomeCode());

        var victimWin = v35();
        victimWin.enterRound(15);
        victimWin.applyTrustScore(35);
        victimWin.finishV35(OutcomeCode.VICTIM_WIN, "第15轮结束判定", 35, NOW);
        assertEquals("VICTIM", victimWin.getWinner());
        assertEquals(35, victimWin.getFinalTrustScore());
    }

    @Test
    void v35RejectsALegacyOutcomeAndAnOutOfRangeScore() {
        var session = v35();
        assertThrows(IllegalArgumentException.class, () -> session.finishV35(OutcomeCode.DEFRAUDED, "旧结局", 50, NOW));
        assertThrows(IllegalArgumentException.class, () -> session.finishV35(OutcomeCode.SCAMMER_WIN, "越界", 101, NOW));
    }

    @Test
    void legacyV34StillEndsOnItsOwnRules() {
        var session = v34();
        assertEquals(20, SimulationRuleset.maxRounds(session));
        assertFalse(SimulationRuleset.isV35(session));
        var decision = OutcomeRules.decideV2(20, null, null, false, true);
        assertEquals(OutcomeCode.DEFRAUDED, decision.outcome());
        session.finish(OutcomeCode.DEFRAUDED, "小额投入后达到上限", com.aiscam.antiscam.simulation.domain.LimitReason.ROUND_LIMIT, NOW);
        assertNull(session.getWinner());
        assertNull(session.getFinalTrustScore());
    }
}
