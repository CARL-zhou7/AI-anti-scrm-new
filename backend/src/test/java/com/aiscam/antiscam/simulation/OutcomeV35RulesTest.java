package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.rules.OutcomeRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PRD-V3.5 verdict: no decision before round 15, then two-way win/lose from the final trust score and the actual net
 * loss. Never a limit reason, never an early end.
 */
class OutcomeV35RulesTest {
    private static OutcomeCode decide(int roundNo, int finalScore, int loss) {
        var decision = OutcomeRules.decideV35(roundNo, finalScore, loss);
        return decision == null ? null : decision.outcome();
    }

    @Test
    void scoreEightyWinsWithoutLoss() {
        assertEquals(OutcomeCode.SCAMMER_WIN, decide(15, 80, 0));
    }

    @Test
    void scoreEightyWinsWithLoss() {
        assertEquals(OutcomeCode.SCAMMER_WIN, decide(15, 80, 500));
    }

    @Test
    void anyNetLossWinsEvenWithLowScore() {
        assertEquals(OutcomeCode.SCAMMER_WIN, decide(15, 79, 1));
    }

    @Test
    void lowScoreAndNoLossLoses() {
        assertEquals(OutcomeCode.VICTIM_WIN, decide(15, 79, 0));
    }

    @Test
    void roundFourteenNeverDecides() {
        assertNull(decide(14, 80, 0));
        assertNull(decide(1, 100, 1000));
    }

    @Test
    void zeroScoreWithoutLossLoses() {
        assertEquals(OutcomeCode.VICTIM_WIN, decide(15, 0, 0));
    }

    @Test
    void perfectScoreWins() {
        assertEquals(OutcomeCode.SCAMMER_WIN, decide(15, 100, 0));
    }

    @Test
    void noLossValueReplacesANegativeLoss() {
        // A negative net loss is impossible; the caller passes 0 instead and the verdict stays victim.
        assertEquals(OutcomeCode.VICTIM_WIN, decide(15, 79, 0));
        var decision = OutcomeRules.decideV35(15, 79, 0);
        assertNotNull(decision);
        assertNull(decision.limitReason(), "a normal verdict never carries a limit reason");
        assertTrue(decision.reason().contains("第15轮"));
    }
}
