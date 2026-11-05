package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.FactCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V12AgentRulesTest {
    @Test void v3UsesProgramGoalEvenWhenNarratorClaimsSuccess() {
        var result = AgentOutputValidator.validate(judgement(false,true,false,null,false,null),
                ScenarioCode.FAKE_INVESTMENT,7,List.of(),PriorFacts.none(),empty,false,true);
        assertFalse(result.judgement().goalAchieved());
        assertFalse(result.judgement().actualFraudOccurred());
    }
    private final FundsSummary empty = new FundsSummary(100,100,0,0,0,0,0);
    private NarratorJudgement judgement(boolean fraud, boolean goal, boolean stopped, OutcomeCode outcome, boolean exit, String reason) {
        return new NarratorJudgement("a","b","c","d",25,"e",fraud,goal,stopped,List.of(),outcome,
                outcome == null ? null : "依据",exit,reason);
    }
    @Test void exitNeedsReasonAndCannotOverrideACandidateOrStop() {
        assertDoesNotThrow(() -> AgentOutputValidator.validate(judgement(false,false,false,null,true,"临时离开"),
                ScenarioCode.TASK_REBATE,3,List.of(),PriorFacts.none(),empty));
        for (var j : List.of(judgement(false,false,false,null,true,null), judgement(false,false,true,null,true,"离开"),
                judgement(false,false,false,OutcomeCode.IDENTIFIED,true,"离开"))) {
            assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(j,ScenarioCode.TASK_REBATE,3,List.of(),PriorFacts.none(),empty));
        }
    }
    @Test void attainedGoalDoesNotRevertAfterRefunds() {
        var e = List.of(new EvidenceItem(FactCode.INITIAL_PAYMENT_COMPLETED,SpeakerRole.VICTIM,EvidenceSource.CONTENT,1,"已转100元",1),
                new EvidenceItem(FactCode.ADDITIONAL_PAYMENT_COMPLETED,SpeakerRole.VICTIM,EvidenceSource.CONTENT,2,"已转900元",1));
        var prior = new PriorFacts(true,true,e);
        assertDoesNotThrow(() -> AgentOutputValidator.validate(judgement(true,true,false,OutcomeCode.DEFRAUDED,false,null),
                ScenarioCode.TASK_REBATE,5,List.of(),prior,new FundsSummary(1000,50,1000,50,950,1,0)));
    }
    @Test void onlyTotalUnavailabilityTerminatesAndLegacyStillRegenerates() {
        var payment = new FundAction(FundFlowType.PAYMENT,FundPurpose.OTHER_PAYMENT,500,5,SpeakerRole.VICTIM,"已转账500元",1,null,null);
        var poor = new FundsContext(ScenarioCode.TASK_REBATE,100,0,0,0,0,List.of());
        assertThrows(FundsUnavailableException.class, () -> AgentOutputValidator.funds(SpeakerRole.VICTIM,"已转账500元",null,null,List.of(payment),5,poor,List.of(),true));
        var reserve = new FundsContext(ScenarioCode.TASK_REBATE,100,500,0,0,0,List.of());
        var error = assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.VICTIM,"已转账500元",null,null,List.of(payment),5,reserve,List.of(),true));
        assertTrue(error.retryable());
        error = assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.VICTIM,"已转账500元",null,null,List.of(payment),5,poor,List.of(),false));
        assertTrue(error.retryable());
    }
    @Test void paidFundsCannotBeClassifiedAsNoFraud() {
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(judgement(false,false,false,null,false,null),
                ScenarioCode.TASK_REBATE,5,List.of(),PriorFacts.none(),new FundsSummary(1000,900,100,0,100,0,0)));
        var evidence = new EvidenceItem(FactCode.INITIAL_PAYMENT_COMPLETED,SpeakerRole.VICTIM,EvidenceSource.CONTENT,1,"已付款100元",1);
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(judgement(true,false,false,null,false,null),
                ScenarioCode.TASK_REBATE,5,List.of(),new PriorFacts(true,false,List.of(evidence)),empty));
    }

    @Test void narratorDefaultsExitForOldScriptsButRejectsWrongTypes() {
        String raw = "{\"fraudStage\":\"a\",\"speechAnalysis\":\"b\",\"methodAnalysis\":\"c\",\"victimPsychology\":\"d\",\"riskScore\":25,\"riskReason\":\"e\",\"actualFraudOccurred\":false,\"goalAchieved\":false,\"cooperationStopped\":false,\"evidence\":[],\"candidateOutcome\":null,\"candidateReason\":null}";
        assertFalse(AgentOutputParser.narrator(raw).characterExit());
        String exit = raw.substring(0,raw.length()-1) + ",\"characterExit\":true,\"characterExitReason\":\"离开\"}";
        assertTrue(AgentOutputParser.narrator(exit).characterExit());
        assertThrows(AgentCallException.class, () -> AgentOutputParser.narrator(exit.replace("true", "1")));
    }
}
