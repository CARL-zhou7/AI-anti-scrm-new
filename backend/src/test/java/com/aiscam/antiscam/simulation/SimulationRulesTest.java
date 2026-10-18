package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.LimitReason;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.rules.DeadlineCalculator;
import com.aiscam.antiscam.simulation.rules.FactCode;
import com.aiscam.antiscam.simulation.rules.OutcomeRules;
import com.aiscam.antiscam.simulation.rules.ScenarioGoalRules;
import com.aiscam.antiscam.simulation.rules.ScenarioGoalRules.Fact;
import com.aiscam.antiscam.timing.TimingCalibrator;
import com.aiscam.antiscam.timing.TimingCalibrator.TimingEvidence;
import com.aiscam.antiscam.timing.TimingSampleStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationRulesTest {
    private static final Instant T = Instant.parse("2026-10-18T02:00:00.000Z");

    @Test
    void startedAtIsMappedFromClickNotFromArrival_andNeverFallsBack() {
        var props = new SimulationProperties();
        var store = new TimingSampleStore(props, Clock.fixed(T, ZoneOffset.UTC));
        var calibrator = new TimingCalibrator(store, props);
        var sample = store.issue("http-1", 7);
        var ok = calibrator.calibrate(new TimingEvidence("1.0", sample.sampleId(), 1000.0, 1200.0, 4100.0), "http-1", 7, T.plusMillis(3500));
        assertEquals(T.plusMillis(3000), ok.startedAt());
        assertThrows(ApiException.class, () -> calibrator.calibrate(
                new TimingEvidence("1.0", sample.sampleId(), 1000.0, 1200.0, 4100.0), "http-other", 7, T.plusMillis(3500)));
        assertThrows(ApiException.class, () -> calibrator.calibrate(
                new TimingEvidence("1.0", sample.sampleId(), 1000.0, 900.0, 4100.0), "http-1", 7, T.plusMillis(3500)));
        assertThrows(ApiException.class, () -> calibrator.calibrate(
                new TimingEvidence("1.0", sample.sampleId(), 1000.0, 1200.0, 4100.0), "http-1", 7, T.plusMillis(70_000)));
    }

    @Test
    void pausedDeadlineAddsElapsedUpToRemainingCredit() {
        Instant deadline = T.plusSeconds(600);
        assertEquals(deadline, DeadlineCalculator.effectiveDeadline(deadline, null, 0, T.plusSeconds(100)));
        // 200 s already credited, paused for 150 s -> only 100 s more may count.
        assertEquals(deadline.plusSeconds(100),
                DeadlineCalculator.effectiveDeadline(deadline, T.plusSeconds(100), 200_000, T.plusSeconds(250)));
    }

    @Test
    void outcomePriorityAndLimits() {
        assertEquals(OutcomeCode.GOAL_ACHIEVED, OutcomeRules.expected(true, true, true));
        assertEquals(OutcomeCode.LOSS_STOPPED, OutcomeRules.expected(false, true, true));
        assertEquals(OutcomeCode.NOT_DEFRAUDED, OutcomeRules.expected(false, false, true));
        assertNull(OutcomeRules.expected(false, true, false));
        assertEquals(LimitReason.ROUND_LIMIT, OutcomeRules.decide(20, null, null, false).limitReason());
        assertEquals(LimitReason.TIME_LIMIT, OutcomeRules.decide(4, null, null, true).limitReason());
        assertNull(OutcomeRules.decide(7, null, null, false));
    }

    @Test
    void scenarioGoalsUseOrderAndVerification() {
        var first = new Fact(FactCode.INITIAL_PAYMENT_COMPLETED, 2, 1);
        var more = new Fact(FactCode.ADDITIONAL_PAYMENT_COMPLETED, 2, 2);
        assertTrue(ScenarioGoalRules.goalAchieved(ScenarioCode.TASK_REBATE, List.of(first, more)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.TASK_REBATE, List.of(first)));
        var transfer = new Fact(FactCode.LOAN_TRANSFER_COMPLETED, 6, 1);
        var verified = new Fact(FactCode.INDEPENDENT_VERIFICATION_COMPLETED, 5, 1);
        assertTrue(ScenarioGoalRules.goalAchieved(ScenarioCode.IMPERSONATION_LOAN, List.of(transfer)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.IMPERSONATION_LOAN, List.of(verified, transfer)));
    }
}
