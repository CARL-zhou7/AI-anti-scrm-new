package com.aiscam.antiscam.simulation.engine;

import com.aiscam.antiscam.agent.AgentCallException;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.AgentRunner;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.media.SentenceSegmenter;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.media.TtsClient;
import com.aiscam.antiscam.media.TtsClient.SynthesizedAudio;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.DeadlineCalculator;
import com.aiscam.antiscam.simulation.rules.OutcomeRules;
import com.aiscam.antiscam.simulation.service.EvidenceJson;
import com.aiscam.antiscam.simulation.service.SimulationTransitions;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.sse.SimulationEventHub;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Orchestration engine (plan Task 8/9). Pipeline principle: <em>persist and publish strictly in order, compute ahead
 * of time</em>. Candidate lines, audio and analyses are prepared in an in-memory preparation area while the
 * previous step is being presented; they are only saved (via {@link SimulationTransitions}) when their phase is
 * reached, and only published after commit. {@link #drive(long)} is idempotent: it reads the persisted position and
 * performs the next possible step, so receipts, timers, resume and finished preparations can all simply call it.
 */
@Component
public class SimulationOrchestrator implements SimulationSignals {
    private static final Logger log = LoggerFactory.getLogger(SimulationOrchestrator.class);
    private static final int MAX_REGENERATIONS = 2;

    private final SimulationTransitions transitions;
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final AgentPromptRepository prompts;
    private final ConfigurationSnapshots snapshots;
    private final AgentRunner runner;
    private final TtsClient tts;
    private final TempAudioStore audio;
    private final SimulationEventHub hub;
    private final SimulationProperties properties;
    private final ThreadPoolTaskScheduler scheduler;
    private final SessionLanes lanes;
    private final Clock clock;
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Long, Live> lives = new ConcurrentHashMap<>();

    public SimulationOrchestrator(SimulationTransitions transitions, SimulationSessionRepository sessions,
            SimulationRoundRepository rounds, SimulationMessageRepository messages, AgentPromptRepository prompts,
            ConfigurationSnapshots snapshots, AgentRunner runner, TtsClient tts, TempAudioStore audio,
            SimulationEventHub hub, SimulationProperties properties, ThreadPoolTaskScheduler simulationScheduler,
            SessionLanes lanes, Clock clock) {
        this.transitions = transitions;
        this.sessions = sessions;
        this.rounds = rounds;
        this.messages = messages;
        this.prompts = prompts;
        this.snapshots = snapshots;
        this.runner = runner;
        this.tts = tts;
        this.audio = audio;
        this.hub = hub;
        this.properties = properties;
        this.scheduler = simulationScheduler;
        this.lanes = lanes;
        this.clock = clock;
    }

    /** Prepared role line; {@code audio == null} with a line means synthesis failed (text is still saved). */
    record PreparedLine(String line, String action, String note, SynthesizedAudio audio, InterruptionCode failure) {
        static PreparedLine failed(InterruptionCode code) { return new PreparedLine(null, null, null, null, code); }
    }

    record PreparedAnalysis(ValidatedJudgement judgement, InterruptionCode failure) {}

    /** In-memory state of one running simulation; discarded (alive=false) at any terminal state. */
    static final class Live {
        final long id;
        volatile boolean alive = true;
        AgentScene scene;
        String scammerVoice, victimVoice;
        final Map<Integer, CompletableFuture<PreparedLine>> scammer = new ConcurrentHashMap<>();
        final Map<Integer, CompletableFuture<PreparedLine>> victim = new ConcurrentHashMap<>();
        final Map<Integer, CompletableFuture<PreparedAnalysis>> analysis = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        ScheduledFuture<?> readingTimer, deadlineTimer;
        volatile Instant lastClientActivity;
        Live(long id) { this.id = id; }
        int nextAttempt(AgentRole role, int round) {
            return attempts.computeIfAbsent(role + ":" + round, k -> new AtomicInteger()).getAndIncrement();
        }
    }

    // ------------------------------------------------------------------ signals

    @Override
    public void started(long sessionId) {
        lanes.run(sessionId, () -> {
            var session = sessions.findById(sessionId).orElse(null);
            if (session == null || !session.isRunning()) return;
            Live live = load(session);
            lives.put(sessionId, live);
            scheduleDeadline(live, session);
            prepareScammer(live, 1, List.of());
        });
    }

    @Override
    public void changed(long sessionId) { lanes.run(sessionId, () -> drive(sessionId)); }

    @Override
    public void pauseChanged(long sessionId) {
        lanes.run(sessionId, () -> {
            Live live = lives.get(sessionId);
            var session = sessions.findById(sessionId).orElse(null);
            if (live == null || session == null || !session.isRunning()) return;
            if (session.isPaused() && live.readingTimer != null) live.readingTimer.cancel(false);
            scheduleDeadline(live, session);
        });
    }

    @Override
    public void terminated(long sessionId, boolean interrupted) {
        lanes.run(sessionId, () -> {
            Live live = lives.remove(sessionId);
            if (live != null) {
                live.alive = false;
                if (live.readingTimer != null) live.readingTimer.cancel(false);
                if (live.deadlineTimer != null) live.deadlineTimer.cancel(false);
                live.scammer.values().forEach(f -> f.cancel(true));
                live.victim.values().forEach(f -> f.cancel(true));
                live.analysis.values().forEach(f -> f.cancel(true));
            }
            audio.release(sessionId);
            if (interrupted) hub.close(sessionId);
        });
    }

    /** Client-originated progress (receipt, pause, resume, exit) for the connection-lost rule. */
    public void touch(long sessionId) {
        Live live = lives.get(sessionId);
        if (live != null) live.lastClientActivity = clock.instant();
    }

    // ------------------------------------------------------------------ drive

    void drive(long sessionId) {
        try {
            step(sessionId);
        } catch (DataAccessException e) {
            log.error("Simulation {} persistence failed; interrupting", sessionId);
            try { transitions.interrupt(sessionId, InterruptionCode.PERSISTENCE_FAILED); }
            catch (DataAccessException again) { log.error("Simulation {} could not record PERSISTENCE_FAILED", sessionId); }
        }
    }

    private void step(long sessionId) {
        Live live = lives.get(sessionId);
        if (live == null || !live.alive) return;
        var session = sessions.findById(sessionId).orElse(null);
        if (session == null || !session.isRunning() || session.isPaused()) return;
        int n = session.getCurrentRoundNo();
        if (n == 0) return; // waiting for the opening receipt
        var round = rounds.findBySessionIdAndRoundNo(sessionId, n).orElseThrow();
        switch (round.getPhase()) {
            case SCAMMER_GENERATING -> {
                var future = live.scammer.get(n);
                if (future == null) { prepareScammer(live, n, dbLines(sessionId)); return; }
                if (!future.isDone()) return;
                var prepared = future.join();
                if (savePresent(live, n, SpeakerRole.SCAMMER, prepared)) startVictim(live, n);
            }
            case SCAMMER_PRESENTING -> {
                resumePending(live, round, SpeakerRole.SCAMMER, live.scammer.get(n));
                if (!live.victim.containsKey(n)) startVictim(live, n);
            }
            case VICTIM_GENERATING -> {
                var future = live.victim.get(n);
                if (future == null) { startVictim(live, n); return; }
                if (!future.isDone()) return;
                savePresent(live, n, SpeakerRole.VICTIM, future.join());
            }
            case VICTIM_PRESENTING -> resumePending(live, round, SpeakerRole.VICTIM, live.victim.get(n));
            case ANALYZING -> {
                var future = live.analysis.get(n);
                if (future == null) { transitions.interrupt(sessionId, InterruptionCode.ANALYSIS_FAILED); return; }
                if (!future.isDone()) return;
                var prepared = future.join();
                if (prepared.failure() != null) { transitions.interrupt(sessionId, prepared.failure()); return; }
                try {
                    transitions.saveAnalysis(sessionId, n, prepared.judgement());
                } catch (IllegalStateException e) {
                    transitions.interrupt(sessionId, InterruptionCode.ANALYSIS_FAILED);
                }
            }
            case ANALYSIS_PRESENTING -> { /* waiting for the analysis receipt */ }
            case COMPLETE -> {
                Instant now = clock.instant();
                if (round.getReadingUntil() != null && round.getReadingUntil().isAfter(now)) {
                    if (live.readingTimer != null) live.readingTimer.cancel(false);
                    live.readingTimer = scheduler.schedule(() -> changed(sessionId), round.getReadingUntil());
                    return;
                }
                if (n < OutcomeRules.MAX_ROUNDS) transitions.createNextRound(sessionId, n);
            }
        }
    }

    /** Saves a prepared line, writes its temp audio and marks it PRESENTING; handles failures per category. */
    private boolean savePresent(Live live, int n, SpeakerRole role, PreparedLine prepared) {
        if (prepared.line() == null) { transitions.interrupt(live.id, prepared.failure()); return false; }
        Long messageId = transitions.saveMessage(live.id, n, role, prepared.line(), prepared.action(), prepared.note());
        if (messageId == null) return false;
        return present(live, messageId, prepared);
    }

    private boolean present(Live live, long messageId, PreparedLine prepared) {
        if (prepared.audio() == null) { transitions.interrupt(live.id, InterruptionCode.TTS_FAILED); return false; }
        try {
            audio.write(live.id, messageId, prepared.audio());
        } catch (RuntimeException e) {
            transitions.interrupt(live.id, InterruptionCode.TTS_FAILED);
            return false;
        }
        return transitions.markPresenting(live.id, messageId);
    }

    /** A message saved but not yet PRESENTING (e.g. paused in between): finish the media step. */
    private void resumePending(Live live, SimulationRound round, SpeakerRole role, CompletableFuture<PreparedLine> future) {
        var message = messages.findByRoundIdAndSpeakerRole(round.getId(), role).orElse(null);
        if (message == null || message.getPresentationStatus() != PresentationStatus.PENDING) return;
        if (future == null || !future.isDone()) { transitions.interrupt(live.id, InterruptionCode.TTS_FAILED); return; }
        present(live, message.getId(), future.join());
    }

    // ------------------------------------------------------------------ preparation area

    private void prepareScammer(Live live, int n, List<DialogueLine> history) {
        if (n > OutcomeRules.MAX_ROUNDS || live.scammer.containsKey(n)) return;
        var future = CompletableFuture.supplyAsync(() -> {
            ScammerTurn turn;
            try {
                turn = retry(live, () -> runner.scammer(live.scene, history, n, remaining(live), live.nextAttempt(AgentRole.SCAMMER, n)));
            } catch (AgentCallException e) {
                return PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED);
            }
            return new PreparedLine(turn.line(), turn.action(), turn.intent(),
                    synthesize(live, live.scammerVoice, turn.tone(), turn.line()), null);
        }, io);
        live.scammer.put(n, future);
        future.whenComplete((r, e) -> changed(live.id));
    }

    /** Starts victim(n) preparation once scammer(n) is saved; includes the BR-P12 minimum-round precheck. */
    private void startVictim(Live live, int n) {
        if (live.victim.containsKey(n)) return;
        List<DialogueLine> saved = dbLines(live.id);
        List<DialogueLine> history = saved.stream().filter(l -> l.roundNo() < n).toList();
        DialogueLine scammerLine = saved.stream().filter(l -> l.roundNo() == n && l.speaker() == SpeakerRole.SCAMMER)
                .findFirst().orElse(null);
        if (scammerLine == null) return;
        PriorFacts prior = priorFacts(live.id, n);
        var victimFuture = new CompletableFuture<PreparedLine>();
        var analysisFuture = new CompletableFuture<PreparedAnalysis>();
        live.victim.put(n, victimFuture);
        live.analysis.put(n, analysisFuture);
        io.execute(() -> {
            try {
                prepareVictim(live, n, history, scammerLine, prior, victimFuture, analysisFuture);
            } catch (RuntimeException e) {
                victimFuture.complete(PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED));
                analysisFuture.complete(new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
            }
        });
        victimFuture.whenComplete((r, e) -> changed(live.id));
        analysisFuture.whenComplete((r, e) -> changed(live.id));
        CompletableFuture.allOf(victimFuture, analysisFuture).whenComplete((ignored, e) -> {
            if (e != null) return;
            PreparedAnalysis r = analysisFuture.join();
            if (r.failure() == null && r.judgement().judgement().candidateOutcome() == null && n < OutcomeRules.MAX_ROUNDS) {
                PreparedLine v = victimFuture.join();
                if (v.line() == null) return;
                lanes.run(live.id, () -> {
                    if (!live.alive) return;
                    List<DialogueLine> lines = new ArrayList<>(dbLines(live.id));
                    if (lines.stream().noneMatch(l -> l.roundNo() == n && l.speaker() == SpeakerRole.VICTIM)) {
                        lines.add(new DialogueLine(n, SpeakerRole.VICTIM, v.line(), v.action(), v.note()));
                    }
                    prepareScammer(live, n + 1, lines);
                });
            }
        });
    }

    private void prepareVictim(Live live, int n, List<DialogueLine> history, DialogueLine scammerLine, PriorFacts prior,
            CompletableFuture<PreparedLine> victimFuture, CompletableFuture<PreparedAnalysis> analysisFuture) {
        VictimTurn accepted = null;
        ValidatedJudgement judged = null;
        for (int regeneration = 0; regeneration <= MAX_REGENERATIONS && accepted == null; regeneration++) {
            boolean regenerate = regeneration > 0;
            VictimTurn candidate;
            try {
                candidate = retry(live, () -> runner.victim(live.scene, history, scammerLine, n, remaining(live), regenerate,
                        live.nextAttempt(AgentRole.VICTIM, n)));
            } catch (AgentCallException e) {
                victimFuture.complete(PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED));
                analysisFuture.complete(new PreparedAnalysis(null, InterruptionCode.DIALOGUE_FAILED));
                return;
            }
            if (n >= OutcomeRules.MIN_ROUNDS) { accepted = candidate; break; }
            // Rounds 1-4: judge the candidate first; a decisive outcome means discard and regenerate (BR-P12).
            List<DialogueLine> lines = withVictim(history, scammerLine, n, candidate);
            try {
                judged = retry(live, () -> runner.narrator(live.scene, lines, n, prior, remaining(live), live.nextAttempt(AgentRole.NARRATOR, n)));
            } catch (AgentCallException e) {
                victimFuture.complete(PreparedLine.failed(InterruptionCode.ANALYSIS_FAILED));
                analysisFuture.complete(new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
                return;
            }
            if (judged.judgement().candidateOutcome() != null) {
                log.info("Simulation {} round {} candidate victim reply discarded by minimum-round precheck (regeneration {})",
                        live.id, n, regeneration + 1);
                judged = null;
                continue;
            }
            accepted = candidate;
        }
        if (accepted == null) {
            victimFuture.complete(PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED));
            analysisFuture.complete(new PreparedAnalysis(null, InterruptionCode.DIALOGUE_FAILED));
            return;
        }
        VictimTurn victim = accepted;
        if (judged != null) {
            analysisFuture.complete(new PreparedAnalysis(judged, null));
        } else {
            List<DialogueLine> lines = withVictim(history, scammerLine, n, victim);
            io.execute(() -> {
                try {
                    var j = retry(live, () -> runner.narrator(live.scene, lines, n, prior, remaining(live), live.nextAttempt(AgentRole.NARRATOR, n)));
                    analysisFuture.complete(new PreparedAnalysis(j, null));
                } catch (RuntimeException e) {
                    analysisFuture.complete(new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
                }
            });
        }
        victimFuture.complete(new PreparedLine(victim.line(), victim.action(), victim.psychology(),
                synthesize(live, live.victimVoice, victim.tone(), victim.line()), null));
    }

    private static List<DialogueLine> withVictim(List<DialogueLine> history, DialogueLine scammerLine, int n, VictimTurn v) {
        List<DialogueLine> lines = new ArrayList<>(history);
        lines.add(scammerLine);
        lines.add(new DialogueLine(n, SpeakerRole.VICTIM, v.line(), v.action(), v.psychology()));
        return lines;
    }

    /** Transparent retries of unpublished content within the remaining budget; results never leave the area. */
    private <T> T retry(Live live, Supplier<T> call) {
        int attempts = properties.getLlm().getTransientRetries() + 1;
        AgentCallException last = null;
        for (int i = 0; i < attempts; i++) {
            if (!live.alive || remaining(live) <= 0) break;
            try { return call.get(); }
            catch (AgentCallException e) {
                last = e;
                if (!e.retryable()) break;
                log.info("Simulation {} agent output rejected ({}), retry {}", live.id, e.getMessage(), i + 1);
            }
        }
        throw last != null ? last : new AgentCallException(AgentCallException.Category.DIALOGUE, false, "no budget");
    }

    private SynthesizedAudio synthesize(Live live, String voice, ToneHint tone, String line) {
        var segments = SentenceSegmenter.split(line);
        for (int i = 0; i <= properties.getLlm().getTransientRetries(); i++) {
            long remaining = remaining(live);
            if (!live.alive || remaining <= 0) return null;
            try {
                return tts.synthesize(voice, tone.ttsSpeed(), segments,
                        Duration.ofMillis(Math.min(properties.getTts().getCallTimeoutMs(), remaining)));
            } catch (TtsClient.TtsException e) {
                log.info("Simulation {} synthesis failed, attempt {}", live.id, i + 1);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ data helpers

    private Live load(SimulationSession session) {
        Live live = new Live(session.getId());
        Map<Long, String> templates = new java.util.HashMap<>();
        prompts.findAllById(List.of(session.getScammerPromptId(), session.getVictimPromptId(), session.getNarratorPromptId()))
                .forEach(p -> templates.put(p.getId(), p.getTemplateContent()));
        var scenario = snapshots.read(session.getScenarioSnapshot());
        var scammer = snapshots.read(session.getScammerSnapshot());
        live.scene = new AgentScene(ScenarioCode.valueOf(scenario.path("scenario_code").asText()), scenario.path("name").asText(),
                scenario.path("description").asText(), snapshots.scammerText(session.getScammerSnapshot()),
                snapshots.victimText(session.getVictimSnapshot()), scammer.path("name").asText(),
                templates.get(session.getScammerPromptId()), templates.get(session.getVictimPromptId()),
                templates.get(session.getNarratorPromptId()));
        var media = snapshots.read(session.getRuntimeSnapshot()).path("media_config");
        live.scammerVoice = media.path("scammer_voice").asText();
        live.victimVoice = media.path("victim_voice").asText();
        return live;
    }

    private List<DialogueLine> dbLines(long sessionId) {
        List<DialogueLine> lines = new ArrayList<>();
        for (var r : rounds.findBySessionIdOrderByRoundNoAsc(sessionId)) {
            messages.findByRoundId(r.getId()).stream().sorted(Comparator.comparing(m -> m.getSpeakerRole().ordinal()))
                    .forEach(m -> lines.add(new DialogueLine(r.getRoundNo(), m.getSpeakerRole(), m.getContent(),
                            m.getActionText(), m.internalNoteForAgents())));
        }
        return lines;
    }

    private PriorFacts priorFacts(long sessionId, int n) {
        if (n <= 1) return PriorFacts.none();
        return rounds.findBySessionIdAndRoundNo(sessionId, n - 1).filter(SimulationRound::hasAnalysis)
                .map(r -> new PriorFacts(r.isActualFraudOccurred(), r.isGoalAchieved(), EvidenceJson.read(r.getEvidenceJson())))
                .orElse(PriorFacts.none());
    }

    private long remaining(Live live) {
        return sessions.findById(live.id).filter(SimulationSession::isRunning)
                .map(s -> DeadlineCalculator.remainingMs(s, clock.instant())).orElse(0L);
    }

    // ------------------------------------------------------------------ deadline & connection

    private void scheduleDeadline(Live live, SimulationSession session) {
        if (live.deadlineTimer != null) live.deadlineTimer.cancel(false);
        live.deadlineTimer = scheduler.schedule(() -> lanes.run(live.id, () -> transitions.convergeIfExpired(live.id)),
                DeadlineCalculator.nextCheck(session));
    }

    /** Fallback sweep for expired (or paused, credit-exhausted) sessions, about once a second. */
    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void deadlineSweep() {
        for (long id : sessions.findRunningDueOrPaused(clock.instant())) lanes.run(id, () -> transitions.convergeIfExpired(id));
    }

    /**
     * CONNECTION_LOST only when all three hold (interface spec §9.1): no stream for longer than the grace window,
     * no client progress since the disconnect, and not enough budget left to finish the content now presenting.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void connectionMonitor() {
        Instant now = clock.instant();
        for (Live live : lives.values()) {
            Instant lost = hub.lastDisconnectedAt(live.id);
            if (hub.activeCount(live.id) > 0 || lost == null) continue;
            if (Duration.between(lost, now).toMillis() <= properties.getConnection().getLostGraceMs()) continue;
            if (live.lastClientActivity != null && live.lastClientActivity.isAfter(lost)) continue;
            lanes.run(live.id, () -> {
                var session = sessions.findById(live.id).orElse(null);
                if (session == null || !session.isRunning() || session.getCurrentRoundNo() == 0) return;
                var round = rounds.findBySessionIdAndRoundNo(live.id, session.getCurrentRoundNo()).orElse(null);
                if (round == null) return;
                long needed = switch (round.getPhase()) {
                    case ANALYSIS_PRESENTING -> 1000;
                    case SCAMMER_PRESENTING, VICTIM_PRESENTING -> messages.findByRoundId(round.getId()).stream()
                            .filter(m -> m.getPresentationStatus() == PresentationStatus.PRESENTING)
                            .mapToLong(m -> audio.meta(live.id, m.getId()).map(TempAudioStore.Meta::durationMs).orElse(0L)).sum();
                    default -> 0;
                };
                if (needed > 0 && DeadlineCalculator.remainingMs(session, clock.instant()) < needed) {
                    transitions.interrupt(live.id, InterruptionCode.CONNECTION_LOST);
                }
            });
        }
    }
}
