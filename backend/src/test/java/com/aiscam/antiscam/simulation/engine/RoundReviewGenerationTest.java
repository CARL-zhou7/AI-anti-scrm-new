package com.aiscam.antiscam.simulation.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.AgentRunner;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.media.TtsClient;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationTransitions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RoundReviewGenerationTest {
    @Test
    void preparesAnalysisDuringPlaybackAndNextDraftDuringReadingWithoutAdvancingEarly() throws Exception {
        var now = Instant.parse("2026-10-29T00:00:00Z");
        var sessions = mock(SimulationSessionRepository.class);
        var session = mock(SimulationSession.class);
        when(session.isRunning()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(1);
        when(sessions.findById(53L)).thenReturn(Optional.of(session));
        var rounds = mock(SimulationRoundRepository.class);
        var round = new SimulationRound(53, 1, now);
        ReflectionTestUtils.setField(round, "id", 9L);
        round.setPhase(RoundPhase.VICTIM_GENERATING);
        when(rounds.findBySessionIdAndRoundNo(53L, 1)).thenReturn(Optional.of(round));
        var transitions = mock(SimulationTransitions.class);
        when(transitions.saveMessage(eq(53L), eq(1), eq(SpeakerRole.VICTIM), any(), any(), any(), any(), any()))
                .thenAnswer(call -> { round.setPhase(RoundPhase.VICTIM_PRESENTING); return 101L; });
        when(transitions.markPresenting(53, 101)).thenReturn(true);
        var runner = mock(AgentRunner.class);
        var started = new java.util.concurrent.CountDownLatch(2);
        var release = new java.util.concurrent.CountDownLatch(1);
        var judgement = mock(ValidatedJudgement.class);
        when(runner.event(any(), anyList(), eq(1), anyLong(), anyInt(), any(), anyString(), anyString()))
                .thenAnswer(call -> { started.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return List.of(); });
        when(runner.narrator(any(), anyList(), eq(1), any(), anyLong(), anyInt(), any(), any(), eq(false), anyList(), anyString(), anyString()))
                .thenAnswer(call -> { started.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return judgement; });
        when(runner.scammer(any(), anyList(), eq(2), anyLong(), anyInt(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new ScammerTurn("下一轮草稿", null, "意图", new ToneHint(Speed.NORMAL, "自然"), null, List.of()));
        var engine = new SimulationOrchestrator(transitions, sessions, rounds, mock(SimulationMessageRepository.class), null, null, runner,
                null, mock(TempAudioStore.class), null, new SimulationProperties(), mock(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class),
                mock(SessionLanes.class), Clock.fixed(now, java.time.ZoneOffset.UTC), mock(com.aiscam.antiscam.simulation.service.TrustEventService.class), null);
        var live = new SimulationOrchestrator.Live(53);
        live.adminTest = true;
        live.scene = mock(AgentScene.class);
        when(live.scene.trustV3()).thenReturn(true);
        live.victim.put(1, CompletableFuture.completedFuture(new SimulationOrchestrator.PreparedLine("回应", null, null,
                new TtsClient.SynthesizedAudio(new byte[] { 1 }, List.of(), 1000), null, null, List.of())));
        register(engine, live);
        try {
            engine.drive(53);
            assertTrue(started.await(2, TimeUnit.SECONDS), "both model calls start before either one finishes");
            release.countDown();
            live.analysis.get(1).get(2, TimeUnit.SECONDS);
            engine.drive(53);
            verify(transitions, never()).saveAnalysis(anyLong(), anyInt(), any(), anyList());
            round.setPhase(RoundPhase.ANALYZING);
            engine.drive(53);
            verify(transitions).saveAnalysis(53, 1, judgement, List.of());
            round.completePresentation(now, now.plusSeconds(5));
            engine.drive(53);
            assertEquals("下一轮草稿", live.scammer.get(2).get(2, TimeUnit.SECONDS).line());
            assertNull(live.scammer.get(2).join().audio(), "review drafts do not synthesize unused audio");
            verify(transitions, never()).createNextRound(anyLong(), anyInt());
            verify(transitions, never()).saveDraft(anyLong(), anyInt(), any(), any(), any(), anyLong(), any(), any());
        } finally {
            release.countDown();
            ((ExecutorService) ReflectionTestUtils.getField(engine, "io")).shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private void register(SimulationOrchestrator engine, SimulationOrchestrator.Live live) {
        ((Map<Long, SimulationOrchestrator.Live>) ReflectionTestUtils.getField(engine, "lives")).put(live.id, live);
    }

    @Test
    void firstRoundPresentsBothLinesAutomaticallyAndSecondRoundWaitsForReview() {
        for (int n : List.of(1, 2)) {
            var sessions = mock(SimulationSessionRepository.class);
            var session = mock(SimulationSession.class);
            when(session.isRunning()).thenReturn(true);
            when(session.getCurrentRoundNo()).thenReturn(n);
            when(session.getUserId()).thenReturn(7L);
            when(sessions.findById(53L)).thenReturn(Optional.of(session));
            var rounds = mock(SimulationRoundRepository.class);
            var round = new SimulationRound(53, n, Instant.EPOCH);
            when(rounds.findBySessionIdAndRoundNo(53L, n)).thenReturn(Optional.of(round));
            when(rounds.findBySessionIdOrderByRoundNoAsc(53L)).thenReturn(List.of());
            var transitions = mock(SimulationTransitions.class);
            var audio = new TtsClient.SynthesizedAudio(new byte[] { 1 }, List.of(), 1000);
            var prepared = new SimulationOrchestrator.PreparedLine("台词", "发送截图", "意图", audio, null, null, List.of());
            when(transitions.saveMessage(eq(53L), eq(n), any(), any(), any(), any(), any(), any())).thenReturn(101L);
            when(transitions.markPresenting(53, 101)).thenReturn(true);
            var engine = new SimulationOrchestrator(transitions, sessions, rounds, null, null, null, null,
                    null, mock(TempAudioStore.class), null, new SimulationProperties(), null, null, Clock.systemUTC(), null, null);
            var live = new SimulationOrchestrator.Live(53);
            live.adminTest = true;
            live.scene = mock(AgentScene.class);
            live.scammer.put(n, CompletableFuture.completedFuture(prepared));
            register(engine, live);
            try {
                engine.drive(53);
                if (n == 1) {
                    verify(transitions).saveMessage(53, 1, SpeakerRole.SCAMMER, "台词", "发送截图", "意图", null, List.of());
                    round.setPhase(RoundPhase.VICTIM_GENERATING);
                    live.victim.put(1, CompletableFuture.completedFuture(prepared));
                    engine.drive(53);
                    verify(transitions).saveMessage(53, 1, SpeakerRole.VICTIM, "台词", "发送截图", "意图", null, List.of());
                    verify(transitions, times(2)).markPresenting(53, 101);
                    verify(transitions, never()).saveDraft(anyLong(), anyInt(), any(), any(), any(), anyLong(), any(), any());
                } else {
                    verify(transitions).saveDraft(53, 2, "台词", "发送截图", null, 7, null, List.of());
                    verify(transitions, never()).saveMessage(anyLong(), anyInt(), any(), any(), any(), any(), any(), any());
                }
            } finally {
                ((ExecutorService) ReflectionTestUtils.getField(engine, "io")).shutdownNow();
            }
        }
    }

    @Test
    void regenerationSendsTheDirectionToTheModelAndReplacesBothSpeechAndAction() throws Exception {
        var sessions = mock(SimulationSessionRepository.class);
        var session = mock(SimulationSession.class);
        when(session.isRunning()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(2);
        when(session.getUserId()).thenReturn(7L);
        when(sessions.findById(53L)).thenReturn(Optional.of(session));
        var rounds = mock(SimulationRoundRepository.class);
        when(rounds.findBySessionIdAndRoundNo(53L, 2)).thenReturn(Optional.of(new SimulationRound(53, 2, Instant.EPOCH)));
        when(rounds.findBySessionIdOrderByRoundNoAsc(53L)).thenReturn(List.of());
        var props = new SimulationProperties();
        var prompt = new AtomicReference<String>();
        var runner = new AgentRunner(request -> {
            prompt.set(request.prompt());
            return "{\"line\":\"我先解释平台流程。\",\"action\":\"发送平台备案截图\",\"intent\":\"解释流程\","
                    + "\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"claimedRefundYuan\":null,\"fundActions\":[]}";
        }, props.getLlm());
        var lanes = mock(SessionLanes.class);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; }).when(lanes).run(eq(53L), any());
        var transitions = mock(SimulationTransitions.class);
        when(transitions.fundsContext(53L)).thenReturn(new FundsContext(ScenarioCode.TASK_REBATE,
                3000, 9000, 0, 0, 0, List.of(), 0, 7500, 1500, 0, 0,
                new com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate(50, 60, 80, 90, 100)));
        var engine = new SimulationOrchestrator(transitions, sessions, rounds, null, null, null, runner,
                mock(TtsClient.class), null, null, props, null, lanes, Clock.systemUTC(), null, null);
        var live = new SimulationOrchestrator.Live(53);
        live.adminTest = true;
        String template;
        try (var input = getClass().getResourceAsStream("/prompts/v7/scammer.txt")) {
            template = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        live.scene = new AgentScene(ScenarioCode.TASK_REBATE, "刷单返利", "简介", "联系人", "本人", "test",
                template, "unused", "unused", "公开背景", true, "unused", true);
        live.scammer.put(2, CompletableFuture.completedFuture(new SimulationOrchestrator.PreparedLine(
                "旧台词", "旧动作", null, null, null, null, List.of())));
        register(engine, live);
        try {
            String guidance = "先解释流程，再发送平台备案截图";
            engine.draftDiscarded(53, 2, guidance);
            var prepared = live.scammer.get(2).get(2, TimeUnit.SECONDS);
            assertEquals("我先解释平台流程。", prepared.line());
            assertEquals("发送平台备案截图", prepared.action());
            assertTrue(prompt.get().contains(guidance));
            assertTrue(prompt.get().contains("完整话术line和行为action"));
            verify(transitions, timeout(2000)).saveDraft(53, 2, prepared.line(), prepared.action(), guidance, 7, prepared.claimedRefundYuan(), prepared.fundActions());
        } finally {
            ((ExecutorService) ReflectionTestUtils.getField(engine, "io")).shutdownNow();
        }
    }
}
