package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScammerRetryTest {
    private AgentScene scene() throws Exception {
        String template;
        try (var in = getClass().getResourceAsStream("/prompts/v7/scammer.txt")) {
            template = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return new AgentScene(ScenarioCode.TASK_REBATE, "刷单返利", "简介", "联系人", "本人", "test",
                template, "unused", "unused", "公开背景", true, "unused", true);
    }
    private FundsContext funds(List<FundFlow> flows) {
        int rewarded = flows.isEmpty() ? 0 : 8;
        return new FundsContext(ScenarioCode.TASK_REBATE, 3000 + rewarded, 9000, 0, 0, 0, flows,
                rewarded, 7500, 1500, 0, 0, new ActionGate.TrustGate(50, 60, 80, 90, 100));
    }
    private SimulationOrchestrator orchestrator(SimulationProperties props) {
        var sessions = mock(SimulationSessionRepository.class);
        var session = mock(SimulationSession.class);
        when(session.isRunning()).thenReturn(true);
        when(session.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(120));
        when(sessions.findById(53L)).thenReturn(Optional.of(session));
        return new SimulationOrchestrator(null, sessions, null, null, null, null, null,
                null, null, null, props, null, null, Clock.systemUTC(), null, null);
    }
    private String output(String quote, boolean post) {
        var actions = post ? List.of(new FundAction(FundFlowType.REWARD, FundPurpose.REWARD, 8, 6,
                SpeakerRole.SCAMMER, quote, 1, null, null)) : List.<FundAction>of();
        try {
            return new ObjectMapper().writeValueAsString(new ScammerTurn(quote, null, "回应当前沟通",
                    new ToneHint(Speed.NORMAL, "自然"), 8, actions));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test void bothFundRejectionsReachTheNextAttemptAndValidPayoutPasses() throws Exception {
        var props = new SimulationProperties();
        props.getLlm().setTransientRetries(2);
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> {
            prompts.add(request.prompt());
            return output(switch (prompts.size()) {
                case 1 -> "做完发8元";
                case 2 -> "8元到账";
                default -> "已实际发放8元报酬";
            }, true);
        }, props.getLlm());
        var live = new SimulationOrchestrator.Live(53);
        var scene = scene();
        var funds = funds(List.of());
        var turn = orchestrator(props).retry(live, feedback -> runner.scammer(scene, List.of(), 6,
                10000, live.nextAttempt(AgentRole.SCAMMER, 6), funds, "", feedback));
        assertEquals(3, prompts.size());
        assertTrue(prompts.get(1).contains(output("做完发8元", true)));
        assertTrue(prompts.get(2).contains(output("8元到账", true)));
        assertFalse(prompts.get(2).contains(output("做完发8元", true)));
        assertFalse(prompts.getFirst().contains("上次输出未通过校验"));
        assertTrue(prompts.get(1).contains("校验原因：fund quote is not a new executed action"));
        assertTrue(prompts.get(2).contains("校验原因：reward is not a new executed payout"));
        assertTrue(prompts.get(2).contains("不能靠改写成已付款来凑通过"));
        assertEquals(1, turn.fundActions().size());
        assertEquals(8, FundRules.apply(funds.summary(), funds, 6, 1, turn.fundActions()).after().totalRewardedYuan());
    }

    @Test void amountAndFieldErrorsCanBeCorrectedWithinTheSameThreeAttemptBudget() throws Exception {
        var props = new SimulationProperties();
        props.getLlm().setTransientRetries(2);
        var prompts = new ArrayList<String>();
        var mapper = new ObjectMapper();
        String valid = output("已实际发放8元报酬", true);
        var wrongFields = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(valid);
        wrongFields.remove("intent");
        wrongFields.put("psychology","PRIVATE_INTENT_SENTINEL");
        String invalidFields = mapper.writeValueAsString(wrongFields);
        var runner = new AgentRunner(request -> {
            prompts.add(request.prompt());
            return switch (prompts.size()) {
                case 1 -> output("已实际发放八元报酬", true);
                case 2 -> invalidFields;
                default -> valid;
            };
        },props.getLlm());
        var live = new SimulationOrchestrator.Live(53);
        var scene = scene();
        var context = funds(List.of());
        var turn = orchestrator(props).retry(live,feedback -> runner.scammer(scene,List.of(),6,10000,
                live.nextAttempt(AgentRole.SCAMMER,6),context,"",feedback));
        assertEquals(3,prompts.size());
        assertTrue(prompts.get(1).contains("quote is missing the Arabic amount matching amountYuan"));
        assertTrue(prompts.get(2).contains("missing fields: intent; extra fields: psychology"));
        for (String prompt : prompts) {
            assertTrue(prompt.contains("【提交前结构自检】"));
            assertTrue(prompt.contains("action无动作时为null，但字段必须保留"));
            assertTrue(prompt.lastIndexOf("【提交前结构自检】") > prompt.lastIndexOf("【上次被拒绝的输出"));
        }
        assertEquals(8,FundRules.apply(context.summary(),context,6,1,turn.fundActions()).after().totalRewardedYuan());
    }

    @Test void receiptOfAnExistingRewardCanBeCorrectedWithoutAnotherPosting() throws Exception {
        var posted = new FundFlow(53, 5, 1L, new FundPosting(FundFlowType.REWARD, FundPurpose.REWARD,
                8, 1, 3008, null, null), Instant.EPOCH);
        var funds = funds(List.of(posted));
        var props = new SimulationProperties();
        props.getLlm().setTransientRetries(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var runner = new AgentRunner(request -> output("8元已经到账，请查收", calls.incrementAndGet() == 1), props.getLlm());
        var scene = scene();
        var live = new SimulationOrchestrator.Live(53);
        var turn = orchestrator(props).retry(live, feedback -> runner.scammer(scene, List.of(), 6,
                10000, live.nextAttempt(AgentRole.SCAMMER, 6), funds, "", feedback));
        assertEquals(2, calls.get());
        assertTrue(turn.fundActions().isEmpty());
        var result = FundRules.apply(funds.summary(), funds, 6, 2, turn.fundActions());
        assertTrue(result.postings().isEmpty());
        assertEquals(8, result.after().totalRewardedYuan());
        assertEquals(3008, result.after().availableBalanceYuan());
    }

    @Test void persistentInvalidPayoutStillFailsWithoutExtraRequests() throws Exception {
        var scene = scene();
        for (int retries : List.of(0, 2)) {
            var props = new SimulationProperties();
            props.getLlm().setTransientRetries(retries);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var runner = new AgentRunner(request -> { calls.incrementAndGet(); return output("8元到账", true); }, props.getLlm());
            var live = new SimulationOrchestrator.Live(53);
            assertThrows(AgentCallException.class, () -> orchestrator(props).retry(live,
                    feedback -> runner.scammer(scene, List.of(), 6, 10000, live.nextAttempt(AgentRole.SCAMMER, 6),
                            funds(List.of()), "", feedback)));
            assertEquals(retries + 1, calls.get());
        }
    }
    @Test void unexpectedPreparationFailuresCompleteTheFutureInsteadOfHanging() throws Exception {
        for (RuntimeException error : List.of(new IllegalStateException("invalid template"),
                new org.springframework.dao.DataAccessResourceFailureException("database unavailable"))) {
            var props = new SimulationProperties();
            var sessions = mock(SimulationSessionRepository.class);
            var session = mock(SimulationSession.class);
            when(session.isRunning()).thenReturn(true);
            when(session.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(120));
            when(sessions.findById(53L)).thenReturn(Optional.of(session));
            var transitions = mock(com.aiscam.antiscam.simulation.service.SimulationTransitions.class);
            when(transitions.fundsContext(53L)).thenThrow(error);
            var engine = new SimulationOrchestrator(transitions,sessions,null,null,null,null,mock(AgentRunner.class),
                    null,null,null,props,null,mock(SessionLanes.class),Clock.systemUTC(),null,null);
            var live = new SimulationOrchestrator.Live(53);
            live.scene = scene();
            try {
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(engine,"prepareScammer",live,5,List.of());
                var prepared = live.scammer.get(5).get(2,java.util.concurrent.TimeUnit.SECONDS);
                assertNull(prepared.line());
                assertEquals(error instanceof org.springframework.dao.DataAccessException
                        ? InterruptionCode.PERSISTENCE_FAILED : InterruptionCode.DIALOGUE_FAILED,prepared.failure());
            } finally {
                ((java.util.concurrent.ExecutorService) org.springframework.test.util.ReflectionTestUtils.getField(engine,"io")).shutdownNow();
            }
        }
    }
}
