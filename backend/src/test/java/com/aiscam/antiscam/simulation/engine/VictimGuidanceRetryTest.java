package com.aiscam.antiscam.simulation.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.VictimGuidance;
import com.aiscam.antiscam.simulation.service.SimulationTransitions;
import com.aiscam.antiscam.simulation.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VictimGuidanceRetryTest {
    @Test void rejectedMatchReachesNextAttemptAndPersistentFailureStillStopsPreparation() {
        var props = new SimulationProperties();
        props.getLlm().setTransientRetries(1);
        var sessions = mock(SimulationSessionRepository.class);
        var session = mock(SimulationSession.class);
        when(session.isRunning()).thenReturn(true);
        when(session.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(120));
        when(sessions.findById(144L)).thenReturn(Optional.of(session));
        var snapshots = new ConfigurationSnapshots(new ObjectMapper());
        when(session.getSceneRuleSnapshot()).thenReturn(snapshots.write(List.of(
                new SceneRuleSnapshot("4", "同请求", "配合请求", 1, "VICTIM"))));
        var rounds = mock(SimulationRoundRepository.class);
        when(rounds.findBySessionIdAndRoundNo(144L, 9)).thenReturn(Optional.of(new SimulationRound(144, 9, Instant.now())));
        var transitions = mock(SimulationTransitions.class);
        when(transitions.victimGuidanceHistory(144L, 9)).thenReturn(List.of());
        var match = new VictimGuidance.Match("4", 8, "请转2000元给模拟商户", 2000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "REFUSED", "暂不转账", false, "");
        doThrow(new IllegalArgumentException("request quote must come from current scammer message"))
                .when(transitions).recordVictimGuidance(144L, 9, match);
        var runner = mock(AgentRunner.class);
        var feedbacks = new ArrayList<String>();
        when(runner.victimRuleMatch(any(), anyString(), anyString(), anyList(), any(), eq(9), anyLong(), anyInt(), anyString()))
                .thenAnswer(call -> { feedbacks.add(call.getArgument(8)); return match; });
        var live = new SimulationOrchestrator.Live(144);
        live.scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT, "投资", "", "", "", "default",
                "", "", "", "", true, "", true, false, true);
        var engine = new SimulationOrchestrator(transitions, sessions, rounds, null, null, snapshots, runner,
                null, null, null, props, null, null, Clock.systemUTC(), null, null);
        var error = assertThrows(AgentCallException.class, () -> ReflectionTestUtils.invokeMethod(engine,
                "prepareVictim", live, 9, List.of(), new DialogueLine(9, SpeakerRole.SCAMMER, match.requestQuote(), null, null),
                PriorFacts.none(), null, null));
        assertEquals(2, feedbacks.size()); assertEquals("", feedbacks.getFirst());
        assertTrue(feedbacks.get(1).contains(snapshots.write(match)));
        assertTrue(feedbacks.get(1).contains(error.getMessage()));
        assertFalse(error.getMessage().contains(match.requestQuote()));
        verify(transitions, never()).fundsContext(anyLong());
    }
}
