package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NarratorRetryTest {
    @Test void oversizedIntentUsesFullAnalysisWithoutConsumingTheRetryBudget() throws Exception {
        var props=new SimulationProperties();props.getLlm().setTransientRetries(1);
        String template;
        try(var in=getClass().getResourceAsStream("/prompts/v18/narrator.txt")) {
            template=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var scene=new AgentScene(com.aiscam.antiscam.content.domain.ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                "","",template,"公开背景",true,null,true,false,true);
        var mapper=new ObjectMapper();var original=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(output(null));
        original.put("methodShort","虚构收益诱导缴费");
        original.put("intentShort","利用虚假后台收益截图营造已经获利的假象并声称需要先缴纳额外手续费才能解冻和提现以诱导继续付款");
        assertTrue(original.path("intentShort").asText().codePointCount(0,original.path("intentShort").asText().length())>40);
        var prompts=new java.util.ArrayList<String>();
        var runner=new AgentRunner(request->{prompts.add(request.prompt());return original.toString();},props.getLlm());
        var live=new SimulationOrchestrator.Live(50);
        var funds=new com.aiscam.antiscam.simulation.domain.FundsContext(scene.scenarioCode(),3000,9000,0,0,0,List.of());
        var result=orchestrator(props).retry(live,feedback->runner.narrator(scene,List.of(),7,PriorFacts.none(),10000,
                live.nextAttempt(AgentRole.NARRATOR,7),funds,null,false,List.of(),"",feedback));
        assertEquals(1,prompts.size());
        assertNull(result.judgement().intentShort());
        assertEquals(original.path("methodShort").asText(),result.judgement().methodShort());
        assertEquals(original.path("speechAnalysis").asText(),result.judgement().speechAnalysis());
        assertEquals(original.path("methodAnalysis").asText(),result.judgement().methodAnalysis());
    }

    private static final String LINE = "名额先不用留，工商信息我还没查完，查清楚再谈。";
    private static final List<DialogueLine> LINES = List.of(
            new DialogueLine(5, SpeakerRole.VICTIM, "我还要核对一下", null, "历史心理"),
            new DialogueLine(6, SpeakerRole.SCAMMER, "今晚截止，先留名额", null, "内部意图"),
            new DialogueLine(6, SpeakerRole.VICTIM, LINE, null, "内部心理不可作为证据"));
    private static final AgentScene SCENE = new AgentScene(ScenarioCode.FAKE_INVESTMENT,
            "投资", "简介", "联系人", "本人", "test", "unused", "unused",
            template(), "公开背景", false, "unused", true);

    private static String template() {
        try (var in = NarratorRetryTest.class.getResourceAsStream("/prompts/v1/narrator.txt")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private SimulationOrchestrator orchestrator(SimulationProperties props) {
        var sessions = mock(SimulationSessionRepository.class);
        var session = mock(SimulationSession.class);
        when(session.isRunning()).thenReturn(true);
        when(session.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(120));
        when(sessions.findById(50L)).thenReturn(Optional.of(session));
        return new SimulationOrchestrator(null, sessions, null, null, null, null, null,
                null, null, null, props, null, null, Clock.systemUTC(), null, null);
    }

    private String output(String quote) {
        var events = quote == null ? List.<TrustEventCandidate>of() : List.of(new TrustEventCandidate(
                TrustEventType.INDEPENDENT_DOUBT, "doubt:工商信息", quote, EvidenceSource.CONTENT));
        var judgement = new NarratorJudgement("核对信息", "限时施压", "名额催促", "尚在核实", 25,
                "暂停配合核实", false, false, false, List.of(), null, null, false, null, events);
        var mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode json = mapper.valueToTree(judgement);
        for (var event : json.withArray("attitudeEvents")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) event).remove(List.of("materials", "requirement"));
        }
        return json.toString();
    }

    @Test void invalidQuoteIsRejectedThenRetryReceivesReasonAndExactCurrentSources() {
        var props = new SimulationProperties();
        props.getLlm().setTransientRetries(1);
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> {
            prompts.add(request.prompt());
            return output(prompts.size() == 1 ? "工商信息尚未核实完成" : "工商信息我还没查完");
        }, props.getLlm());
        var live = new SimulationOrchestrator.Live(50);
        var result = orchestrator(props).retry(live, feedback -> runner.narrator(SCENE, LINES, 6,
                PriorFacts.none(), 10000, live.nextAttempt(AgentRole.NARRATOR, 6), null, null,
                false, List.of(), "", feedback));
        assertEquals(2, prompts.size());
        assertFalse(prompts.getFirst().contains("上次输出未通过校验"));
        assertTrue(prompts.get(1).contains("attitude quote not in current message"));
        String sources = prompts.get(1).split("【本轮态度事件可引用原文】", 2)[1];
        assertTrue(sources.contains(LINE));
        assertFalse(sources.contains("历史心理"));
        assertFalse(sources.contains("内部心理"));
        assertFalse(sources.contains("今晚截止"));
        assertEquals("工商信息我还没查完", result.judgement().attitudeEvents().getFirst().quote());
    }

    @Test void continuedVerificationMayReturnNoNewAttitudeWithoutEndingTheRound() {
        var props = new SimulationProperties();
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains("同一疑点持续核实不算新的独立态度变化"));
            return output(null);
        }, props.getLlm());
        var result = runner.narrator(SCENE, LINES, 6, PriorFacts.none(), 10000, 1, null,
                null, false, List.of(), "", "attitude quote not in current message");
        assertTrue(result.judgement().attitudeEvents().isEmpty());
        assertNull(result.judgement().candidateOutcome());
        assertEquals(25, result.judgement().riskScore());
    }

    @Test void invalidEvidenceStillFailsWithinTheConfiguredRequestLimit() {
        for (int retries : List.of(0, 1)) {
            var props = new SimulationProperties();
            props.getLlm().setTransientRetries(retries);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var runner = new AgentRunner(request -> { calls.incrementAndGet(); return output("内部心理不可作为证据"); }, props.getLlm());
            var live = new SimulationOrchestrator.Live(50);
            assertThrows(AgentCallException.class, () -> orchestrator(props).retry(live,
                    feedback -> runner.narrator(SCENE, LINES, 6, PriorFacts.none(), 10000,
                            live.nextAttempt(AgentRole.NARRATOR, 6), null, null, false, List.of(), "", feedback)));
            assertEquals(retries + 1, calls.get());
        }
    }
}
