package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.media.TtsClient;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import com.aiscam.antiscam.simulation.service.*;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EarlyGoalTest {
    private static final String QUOTE = "已实际转账20000元";
    private final EvidenceItem evidence = new EvidenceItem(FactCode.LOAN_TRANSFER_COMPLETED,SpeakerRole.VICTIM,
            EvidenceSource.ACTION,2,QUOTE,1);
    private NarratorJudgement judgement(boolean goal) {
        return new NarratorJudgement("借款","催促","亲情","相信",25,"已经转账",true,goal,false,
                List.of(evidence),goal ? OutcomeCode.DEFRAUDED : null,goal ? "未独立核实即转账" : null,false,null);
    }
    private String template(String name) throws Exception {
        try (var in=getClass().getResourceAsStream("/prompts/"+name+".txt")) {
            return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private FundsContext funds() {
        return new FundsContext(ScenarioCode.IMPERSONATION_LOAN,30000,0,0,100,0,List.of(),
                0,0,0,0,0,new ActionGate.TrustGate(70,40,50,60,2000));
    }
    private SimulationSession session() {
        var s=mock(SimulationSession.class);
        when(s.isRunning()).thenReturn(true);
        when(s.getCurrentRoundNo()).thenReturn(2);
        when(s.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(120));
        when(s.getEventPromptId()).thenReturn(1L);
        return s;
    }

    @Test void secondRoundPaymentPassesRealPrecheckWithoutVictimRegeneration() throws Exception {
        var props=new SimulationProperties();
        assertNotNull(new db.migration.V31__improve_dialogue_and_payment_progression().getChecksum());
        var sessions=mock(SimulationSessionRepository.class);
        var session=session();
        when(sessions.findById(67L)).thenReturn(Optional.of(session));
        var transitions=mock(SimulationTransitions.class);
        when(transitions.fundsContext(67L)).thenReturn(funds());
        var victimCalls=new AtomicInteger();
        var narratorCalls=new AtomicInteger();
        var mapper=new ObjectMapper();
        var payment=new FundAction(FundFlowType.PAYMENT,FundPurpose.LOAN_TRANSFER,20000,2,
                SpeakerRole.VICTIM,QUOTE,1,null,null,null);
        String victimJson=mapper.writeValueAsString(new VictimTurn("钱已转过去了",QUOTE,"担心孩子",
                new ToneHint(Speed.NORMAL,"自然"),List.of(payment)));
        String narratorJson=mapper.writeValueAsString(judgement(true));
        var runner=new AgentRunner(request -> {
            if (request.role()==AgentRole.VICTIM) {
                assertTrue(request.prompt().contains("不能反过来称对方"));
                assertTrue(request.prompt().contains("不以复述上一句作为固定开头"));
                victimCalls.incrementAndGet(); return victimJson;
            }
            narratorCalls.incrementAndGet(); return narratorJson;
        },props.getLlm());
        var engine=new SimulationOrchestrator(transitions,sessions,null,null,null,null,runner,mock(TtsClient.class),
                null,null,props,null,null,Clock.systemUTC(),mock(TrustEventService.class),null);
        var live=new SimulationOrchestrator.Live(67);
        live.fundsV2=true;
        live.scene=new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"借款","简介","联系人","父亲","test",
                "unused",template("v9/victim"),template("v7/narrator"),"背景",true,"unused",true);
        var victim=new CompletableFuture<SimulationOrchestrator.PreparedLine>();
        var analysis=new CompletableFuture<SimulationOrchestrator.PreparedAnalysis>();
        try {
            ReflectionTestUtils.invokeMethod(engine,"prepareVictim",live,2,List.of(),
                    new DialogueLine(2,SpeakerRole.SCAMMER,"先转我20000元应急",null,""),PriorFacts.none(),victim,analysis);
            assertNull(victim.join().failure());
            assertEquals(List.of(payment),victim.join().fundActions());
            assertEquals(1,victimCalls.get());
            assertEquals(1,narratorCalls.get());
            assertFalse(analysis.isDone()); // Final narration still waits for the committed payment.
        } finally { ((ExecutorService)ReflectionTestUtils.getField(engine,"io")).shutdownNow(); }
        var lines=List.of(new DialogueLine(2,SpeakerRole.VICTIM,"钱已转过去了",QUOTE,""));
        var paid=new FundsSummary(30000,10000,20000,0,20000,0,0);
        var rejected=assertThrows(AgentCallException.class,()->AgentOutputValidator.validate(judgement(false),
                ScenarioCode.IMPERSONATION_LOAN,2,lines,PriorFacts.none(),paid));
        assertTrue(rejected.getMessage().contains("goalAchieved must be true"));
    }

    @Test void savedEarlyGoalIsEligibleForNormalFinishButLegacySessionsRemainRestricted() {
        var sessions=mock(SimulationSessionRepository.class);
        var session=session();
        when(sessions.lockById(67L)).thenReturn(Optional.of(session));
        var rounds=mock(SimulationRoundRepository.class);
        var round=new SimulationRound(67,2,Instant.now());
        ReflectionTestUtils.setField(round,"phase",RoundPhase.ANALYZING);
        ReflectionTestUtils.setField(round,"id",9L);
        when(rounds.findBySessionIdAndRoundNo(67L,2)).thenReturn(Optional.of(round));
        when(rounds.findBySessionIdOrderByRoundNoAsc(67L)).thenReturn(List.of(round));
        var messages=mock(SimulationMessageRepository.class);
        var message=mock(SimulationMessage.class);
        when(message.getId()).thenReturn(12L);
        when(message.getSpeakerRole()).thenReturn(SpeakerRole.VICTIM);
        when(messages.findByRoundId(9L)).thenReturn(List.of(message));
        var transitions=mock(SimulationTransitions.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(transitions,"sessions",sessions);
        ReflectionTestUtils.setField(transitions,"rounds",rounds);
        ReflectionTestUtils.setField(transitions,"messages",messages);
        ReflectionTestUtils.setField(transitions,"clock",Clock.systemUTC());
        ReflectionTestUtils.setField(transitions,"assembler",mock(SimulationStateAssembler.class));
        ReflectionTestUtils.setField(transitions,"hub",mock(SimulationEventHub.class));
        when(session.getCurrentTrustScore()).thenReturn(70);
        when(session.getTrustThresholdAccept()).thenReturn(40);
        when(session.getTrustThresholdSmall()).thenReturn(50);
        when(session.getTrustThresholdLarge()).thenReturn(60);
        assertTrue(transitions.saveAnalysis(67L,2,new ValidatedJudgement(judgement(true),List.of(evidence))));
        assertEquals(82,round.getRiskScore());
        boolean early=ReflectionTestUtils.invokeMethod(transitions,"earlyException",session,round);
        assertTrue(early);
        assertEquals(OutcomeCode.DEFRAUDED,OutcomeRules.decide(2,round.getCandidateOutcome(),
                round.getCandidateReason(),false,round.isActualFraudOccurred(),early).outcome());
        ReflectionTestUtils.setField(round,"phase",RoundPhase.ANALYZING);
        var snapshots = new com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots(new ObjectMapper());
        ReflectionTestUtils.setField(transitions,"snapshots",snapshots);
        when(session.getRuntimeSnapshot()).thenReturn("{\"ruleset_version\":\"PRD-V3.4\"}");
        when(session.getAvailableBalanceYuan()).thenReturn(10000);
        when(session.getId()).thenReturn(67L);
        when(session.getTrustSmallCapYuan()).thenReturn(2000);
        var funds = mock(FundFlowRepository.class);
        ReflectionTestUtils.setField(transitions,"funds",funds);
        var large = new FundFlow(67,9,12L,new FundPosting(FundFlowType.PAYMENT,FundPurpose.LOAN_TRANSFER,20000,1,10000,null,null),Instant.now());
        var small = new FundFlow(67,9,12L,new FundPosting(FundFlowType.PAYMENT,FundPurpose.LOAN_TRANSFER,1000,1,29000,null,null),Instant.now());
        when(funds.findBySessionIdOrderBySequenceNoAsc(67L)).thenReturn(List.of(large));
        assertNotNull(ReflectionTestUtils.invokeMethod(transitions,"decide",session,round,false));
        when(funds.findBySessionIdOrderBySequenceNoAsc(67L)).thenReturn(List.of(small));
        assertNull(ReflectionTestUtils.invokeMethod(transitions,"decide",session,round,false));
        var limit = (OutcomeRules.Decision) ReflectionTestUtils.invokeMethod(transitions,"decide",session,round,true);
        assertEquals(OutcomeCode.DEFRAUDED,limit.outcome());
        assertEquals(LimitReason.TIME_LIMIT,limit.limitReason());
        ReflectionTestUtils.setField(round,"cooperationStopped",1);
        assertNotNull(ReflectionTestUtils.invokeMethod(transitions,"decide",session,round,false));
        ReflectionTestUtils.setField(round,"goalAchieved",0);
        assertEquals(true,ReflectionTestUtils.invokeMethod(transitions,"earlyException",session,round));
        assertNotNull(ReflectionTestUtils.invokeMethod(transitions,"decide",session,round,false));
        var stopped = new NarratorJudgement("止损","拒绝","停止","警觉",100,"已付款后停止",true,false,true,
                List.of(evidence),OutcomeCode.DEFRAUDED,"已经付款，停止追加",false,null);
        assertTrue(AgentOutputValidator.earlyException(stopped,2));
        ReflectionTestUtils.setField(round,"goalAchieved",1);
        ReflectionTestUtils.setField(round,"cooperationStopped",0);
        when(session.getAvailableBalanceYuan()).thenReturn(0);
        assertNotNull(ReflectionTestUtils.invokeMethod(transitions,"decide",session,round,false));
        when(session.getEventPromptId()).thenReturn(null);
        assertEquals(false,ReflectionTestUtils.invokeMethod(transitions,"earlyException",session,round));
        assertThrows(IllegalStateException.class,()->transitions.saveAnalysis(67L,2,
                new ValidatedJudgement(judgement(true),List.of(evidence))));
    }
}
