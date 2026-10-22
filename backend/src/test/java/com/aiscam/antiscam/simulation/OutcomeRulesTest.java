package com.aiscam.antiscam.simulation;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OutcomeRulesTest {
    @Test void candidatesAndLimitsRespectPriorityAndMinimumRounds() {
        assertEquals(OutcomeCode.DEFRAUDED, OutcomeRules.expected(true, true, true));
        assertEquals(OutcomeCode.DEFRAUDED, OutcomeRules.expected(false, true, true));
        assertEquals(OutcomeCode.IDENTIFIED, OutcomeRules.expected(false, false, true));
        assertNull(OutcomeRules.expected(false, true, false));
        assertNull(OutcomeRules.decide(4, OutcomeCode.IDENTIFIED, "停止", false, false));
        assertEquals(OutcomeCode.LIMIT_REACHED, OutcomeRules.decide(3, null, null, true, false).outcome());
        assertEquals(LimitReason.TIME_LIMIT, OutcomeRules.decide(3, null, null, true, true).limitReason());
        assertEquals(OutcomeCode.DEFRAUDED, OutcomeRules.decide(20, null, null, false, true).outcome());
        assertEquals(OutcomeCode.LIMIT_REACHED, OutcomeRules.decide(20, null, null, false, false).outcome());
        assertEquals(LimitReason.ROUND_LIMIT, OutcomeRules.decide(20, null, null, false, false).limitReason());
        assertEquals(LimitReason.BOTH, OutcomeRules.decide(20, null, null, true, true).limitReason());
        assertNull(OutcomeRules.decide(5, OutcomeCode.DEFRAUDED, "达标", true, true).limitReason());
    }
    @Test void rebateNeedsAnAdditionalPaymentAndEitherThreshold() {
        var facts = List.of(new ScenarioGoalRules.Fact(FactCode.INITIAL_PAYMENT_COMPLETED, 1, 1),
                new ScenarioGoalRules.Fact(FactCode.ADDITIONAL_PAYMENT_COMPLETED, 2, 1));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.TASK_REBATE, facts, funds(1000, 0)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.TASK_REBATE, facts, funds(999, 1)));
        assertTrue(ScenarioGoalRules.goalAchieved(ScenarioCode.TASK_REBATE, facts, funds(1000, 1)));
        assertTrue(ScenarioGoalRules.goalAchieved(ScenarioCode.TASK_REBATE, facts, funds(10, 3)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.TASK_REBATE, List.of(facts.getFirst()), funds(1000, 1)));
    }
    @Test void investmentAndLoanGoalsRequireMoneyAndOrderedFacts() {
        var investment = new ScenarioGoalRules.Fact(FactCode.INVESTMENT_COMPLETED, 1, 1);
        var fee = new ScenarioGoalRules.Fact(FactCode.EXTRA_FEE_PAID, 2, 1);
        assertTrue(ScenarioGoalRules.goalAchieved(ScenarioCode.FAKE_INVESTMENT, List.of(investment,fee), funds(20,1)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.FAKE_INVESTMENT, List.of(fee,new ScenarioGoalRules.Fact(FactCode.INVESTMENT_COMPLETED,3,1)), funds(20,1)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.FAKE_INVESTMENT, List.of(investment,fee), funds(20,0)));
        var loan = new ScenarioGoalRules.Fact(FactCode.LOAN_TRANSFER_COMPLETED, 2, 1);
        assertTrue(ScenarioGoalRules.goalAchieved(ScenarioCode.IMPERSONATION_LOAN,List.of(loan),funds(20,0)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.IMPERSONATION_LOAN,List.of(loan),funds(0,0)));
        assertFalse(ScenarioGoalRules.goalAchieved(ScenarioCode.IMPERSONATION_LOAN,List.of(loan,
                new ScenarioGoalRules.Fact(FactCode.INDEPENDENT_VERIFICATION_COMPLETED,1,1)),funds(20,0)));
    }
    private FundsSummary funds(int loss, int additional) { return new FundsSummary(2000, 2000-loss, loss, 0, loss, additional, 0); }
}
