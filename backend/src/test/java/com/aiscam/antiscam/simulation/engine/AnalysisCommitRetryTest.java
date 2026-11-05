package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisCommitRetryTest {
    @Test void saveValidationCanCorrectWithoutResettingTheNarratorCallBudget() throws Exception {
        for (boolean persistent : List.of(false,true)) {
            var props = new SimulationProperties();
            props.getLlm().setTransientRetries(2);
            var sessions = mock(SimulationSessionRepository.class);
            var session = mock(SimulationSession.class);
            when(session.isRunning()).thenReturn(true);
            when(session.getCurrentRoundNo()).thenReturn(5);
            when(session.getDeadlineAt()).thenReturn(Instant.now().plusSeconds(120));
            when(sessions.findById(63L)).thenReturn(Optional.of(session));
            var rounds = mock(SimulationRoundRepository.class);
            var round = mock(SimulationRound.class);
            when(round.getPhase()).thenReturn(RoundPhase.ANALYZING);
            when(rounds.findBySessionIdAndRoundNo(63L,5)).thenReturn(Optional.of(round));
            var transitions = mock(SimulationTransitions.class);
            var judgement = new ValidatedJudgement(new NarratorJudgement("核实","核实","核实","核实",25,"谨慎",
                    false,false,false,List.of(),null,null,false,null),List.of());
            var saves = new AtomicInteger();
            when(transitions.saveAnalysis(63L,5,judgement)).thenAnswer(invocation -> {
                if (saves.incrementAndGet() == 1 || persistent) throw new IllegalArgumentException("Missing waiting item");
                return true;
            });
            var runner = mock(AgentRunner.class);
            when(runner.narrator(any(),anyList(),eq(5),any(),anyLong(),anyInt(),isNull(),isNull(),eq(false),anyList(),eq(""),anyString()))
                    .thenAnswer(invocation -> {
                        assertTrue(invocation.<String>getArgument(11).contains("Missing waiting item"));
                        return judgement;
                    });
            var lanes = mock(SessionLanes.class);
            var scheduler = mock(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class);
            var engine = new SimulationOrchestrator(transitions,sessions,rounds,null,null,null,runner,null,null,null,
                    props,scheduler,lanes,Clock.systemUTC(),null,null);
            var live = new SimulationOrchestrator.Live(63);
            live.fundsV2 = true;
            live.scene = new AgentScene(ScenarioCode.TASK_REBATE,"场景","简介","联系人","本人","test",
                    "unused","unused","unused","背景",false,"unused",false);
            live.finalAnalysisCalls.put(5,new AtomicInteger(1));
            live.analysis.put(5,CompletableFuture.completedFuture(new SimulationOrchestrator.PreparedAnalysis(judgement,null)));
            @SuppressWarnings("unchecked")
            var lives = (Map<Long,SimulationOrchestrator.Live>) ReflectionTestUtils.getField(engine,"lives");
            lives.put(63L,live);
            try {
                int expectedSaves = persistent ? 3 : 2;
                for (int i=0;i<expectedSaves;i++) {
                    engine.drive(63L);
                    live.analysis.get(5).get(2,TimeUnit.SECONDS);
                }
                assertEquals(expectedSaves,saves.get());
                assertEquals(expectedSaves,live.finalAnalysisCalls.get(5).get());
                verify(transitions,never()).interrupt(63L,InterruptionCode.ANALYSIS_FAILED);
                assertEquals(persistent, live.recoveryScheduled);
                if (persistent) verify(scheduler).schedule(any(Runnable.class),any(Instant.class));
                verify(lanes,timeout(2000).atLeastOnce()).run(eq(63L),any());
            } finally {
                ((ExecutorService) ReflectionTestUtils.getField(engine,"io")).shutdownNow();
            }
        }
    }

    @Test void receiptVersionRaceReusesTheSameEventCandidates() {
        var sessions = mock(SimulationSessionRepository.class);
        var session = mock(SimulationSession.class);
        when(session.isRunning()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(5);
        when(session.getStateVersion()).thenReturn(10L,11L);
        when(sessions.findById(63L)).thenReturn(Optional.of(session));
        var rounds = mock(SimulationRoundRepository.class);
        var round = mock(SimulationRound.class);
        when(round.getId()).thenReturn(9L);
        when(rounds.findBySessionIdAndRoundNo(63L,5)).thenReturn(Optional.of(round));
        var messages = mock(SimulationMessageRepository.class);
        var message = mock(SimulationMessage.class);
        when(message.getId()).thenReturn(17L);
        when(messages.findByRoundIdAndSpeakerRole(9L,SpeakerRole.SCAMMER)).thenReturn(Optional.of(message));
        var events = mock(TrustEventService.class);
        List<TrustEventCandidate> candidates = List.of();
        when(events.record(eq(63L),eq(5),eq(17L),anyLong(),same(candidates))).thenReturn(null,List.of());
        var engine = new SimulationOrchestrator(null,sessions,rounds,messages,null,null,null,null,null,null,
                new SimulationProperties(),null,null,Clock.systemUTC(),events,null);
        try {
            assertEquals(List.of(),ReflectionTestUtils.invokeMethod(engine,"recordEvents",new SimulationOrchestrator.Live(63),5,candidates));
            verify(events).record(63L,5,17L,10L,candidates);
            verify(events).record(63L,5,17L,11L,candidates);
            when(events.record(eq(63L),eq(5),eq(17L),anyLong(),same(candidates))).thenReturn(null);
            assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(engine,"recordEvents",
                    new SimulationOrchestrator.Live(63),5,candidates));
            verify(events,times(4)).record(eq(63L),eq(5),eq(17L),anyLong(),same(candidates));
        } finally {
            ((ExecutorService) ReflectionTestUtils.getField(engine,"io")).shutdownNow();
        }
    }
}
