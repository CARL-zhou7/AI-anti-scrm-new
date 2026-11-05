package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationTransitions;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransientRecoveryTest {
    @Test void failedAnalysisSchedulesOneRecoveryAndKeepsCommittedProgress() {
        var sessions = mock(SimulationSessionRepository.class);
        var session = mock(SimulationSession.class);
        when(sessions.findById(205L)).thenReturn(Optional.of(session));
        when(session.isRunning()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(7);
        var rounds = mock(SimulationRoundRepository.class);
        var round = mock(SimulationRound.class);
        when(rounds.findBySessionIdAndRoundNo(205L,7)).thenReturn(Optional.of(round));
        when(round.getPhase()).thenReturn(RoundPhase.ANALYZING);
        var transitions = mock(SimulationTransitions.class);
        var scheduler = mock(ThreadPoolTaskScheduler.class);
        var lanes = mock(SessionLanes.class);
        var engine = new SimulationOrchestrator(transitions,sessions,rounds,null,null,null,null,null,null,null,
                new SimulationProperties(),scheduler,lanes,Clock.systemUTC(),null,null);
        var live = new SimulationOrchestrator.Live(205);
        live.analysis.put(7,CompletableFuture.completedFuture(new SimulationOrchestrator.PreparedAnalysis(null,InterruptionCode.ANALYSIS_FAILED)));
        live.finalAnalysisCalls.put(7,new AtomicInteger(3));
        ReflectionTestUtils.invokeMethod(engine,"recover",live,InterruptionCode.ANALYSIS_FAILED);
        ReflectionTestUtils.invokeMethod(engine,"recover",live,InterruptionCode.ANALYSIS_FAILED);
        var timer = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(timer.capture(),any(Instant.class));
        assertTrue(live.recoveryScheduled);
        timer.getValue().run();
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(lanes).run(eq(205L),task.capture());
        task.getValue().run();
        assertFalse(live.recoveryScheduled);
        assertFalse(live.analysis.containsKey(7));
        assertFalse(live.finalAnalysisCalls.containsKey(7));
        verifyNoInteractions(transitions);
    }
}
