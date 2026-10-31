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
import java.util.function.Function;
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
    private final com.aiscam.antiscam.simulation.service.TrustEventService trustEvents;
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final AgentPromptRepository prompts;
    private final ConfigurationSnapshots snapshots;
    private final AgentRunner runner;
    private final com.aiscam.antiscam.simulation.service.WaitingItemService waiting;
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
            SessionLanes lanes, Clock clock, com.aiscam.antiscam.simulation.service.TrustEventService trustEvents,
            com.aiscam.antiscam.simulation.service.WaitingItemService waiting) {
        this.waiting=waiting;
        this.trustEvents = trustEvents;
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
    record PreparedLine(String line, String action, String note, SynthesizedAudio audio, InterruptionCode failure,
            Integer claimedRefundYuan, List<FundAction> fundActions) {
        static PreparedLine failed(InterruptionCode code) { return new PreparedLine(null, null, null, null, code, null, List.of()); }
    }

    /** V1.4 carries the uncommitted whole-round evidence next to the judgement; nothing is saved before settlement. */
    record PreparedAnalysis(ValidatedJudgement judgement, List<TrustEventCandidate> trustEvents, InterruptionCode failure) {
        PreparedAnalysis(ValidatedJudgement judgement, InterruptionCode failure) { this(judgement, List.of(), failure); }
    }

    /** In-memory state of one running simulation; discarded (alive=false) at any terminal state. */
    static final class Live {
        final long id;
        volatile boolean alive = true;
        AgentScene scene;
        boolean fundsV2;
        boolean waitingV1;
        boolean adminTest;
        String scammerVoice, victimVoice;
        final Map<Integer, CompletableFuture<PreparedLine>> scammer = new ConcurrentHashMap<>();
        final Map<Integer, String> scammerGuidance = new ConcurrentHashMap<>();
        final Map<Integer, CompletableFuture<PreparedLine>> victim = new ConcurrentHashMap<>();
        final Map<Integer, CompletableFuture<PreparedAnalysis>> analysis = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        final Map<Integer, AtomicInteger> finalAnalysisCalls = new ConcurrentHashMap<>();
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

    /**
     * An adopted draft is already saved as a PENDING scammer message. Synthesize its audio outside any transaction and
     * hand the prepared line to the normal presenting step, which then starts the victim.
     */
    @Override
    public void draftAdopted(long sessionId, int roundNo) {
        lanes.run(sessionId, () -> {
            Live live = lives.get(sessionId);
            var session = sessions.findById(sessionId).orElse(null);
            if (live == null || session == null || !session.isRunning()) return;
            var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElse(null);
            if (round == null) return;
            var message = messages.findByRoundIdAndSpeakerRole(round.getId(), SpeakerRole.SCAMMER).orElse(null);
            if (message == null) return;
            if (message.getPresentationStatus() != PresentationStatus.PENDING) return;
            startVictim(live, roundNo);
            var prepared = CompletableFuture.completedFuture(new PreparedLine(message.getContent(),
                    message.getActionText(), message.internalNoteForAgents(),
                    synthesize(live, live.scammerVoice, new ToneHint(Speed.NORMAL, "自然"), message.getContent()),
                    null, null, List.of()));
            live.scammer.put(roundNo, prepared);
            present(live, message.getId(), prepared.join());
        });
    }

    @Override
    public void draftDiscarded(long sessionId, int roundNo, String guidance) {
        lanes.run(sessionId, () -> {
            Live live = lives.get(sessionId);
            if (live != null) {
                live.scammer.remove(roundNo);
                live.scammerGuidance.put(roundNo, guidance);
            }
            drive(sessionId);
        });
    }

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
    public void terminated(long sessionId, boolean interrupted) {        lanes.run(sessionId, () -> {
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
                if (live.adminTest && n > 1) {
                    // Review gate: keep the generated line as a draft and wait; nothing formal is saved yet.
                    if (prepared.line() == null) { transitions.interrupt(live.id, prepared.failure()); return; }
                    transitions.saveDraft(live.id, n, prepared.line(), prepared.action(), live.scammerGuidance.get(n), session.getUserId(),
                            prepared.claimedRefundYuan(), prepared.fundActions());
                    return;
                }
                if (savePresent(live, n, SpeakerRole.SCAMMER, prepared)) startVictim(live, n);
            }
            case SCAMMER_REVIEW -> { /* waiting for the administrator to continue, regenerate or edit */ }
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
                if (future == null) {
                    if (!live.scene.trustV3()) { transitions.interrupt(sessionId, InterruptionCode.ANALYSIS_FAILED); return; }
                    // V3.5: both formal lines, their funds and both presentations are already saved here. Start the
                    // one whole-round settlement exactly once; readiness is the phase, never an unstarted future.
                    var prepared = new CompletableFuture<PreparedAnalysis>();
                    live.analysis.put(n, prepared);
                    prepared.whenComplete((result, error) -> changed(sessionId));
                    prepareFinalAnalysis(live, n);
                    return;
                }
                if (!future.isDone()) return;
                var prepared = future.join();
                if (prepared.failure() != null) { transitions.interrupt(sessionId, prepared.failure()); return; }
                try {
                    if (live.scene.trustV3()) {
                        // V3.5 always settles through the whole-round entry, even with an empty event list.
                        transitions.saveAnalysis(sessionId, n, prepared.judgement(), prepared.trustEvents());
                    } else {
                        transitions.saveAnalysis(sessionId, n, prepared.judgement());
                    }
                } catch (IllegalStateException | IllegalArgumentException | AgentCallException e) {
                    logFailure(sessionId, n, "ANALYSIS_SAVE", e);
                    if (live.fundsV2 && remainingAnalysisAttempts(live, n) > 0) {
                        var corrected = new CompletableFuture<PreparedAnalysis>();
                        live.analysis.put(n, corrected);
                        corrected.whenComplete((result, error) -> changed(sessionId));
                        prepareFinalAnalysis(live, n, "analysis persistence validation: " + e.getMessage());
                    } else transitions.interrupt(sessionId, InterruptionCode.ANALYSIS_FAILED);
                }
            }
            case ANALYSIS_PRESENTING -> { /* waiting for the analysis receipt */ }
            case COMPLETE -> {
                var analysis = live.analysis.get(n);
                if (live.scene.v12() && !live.scene.trustV3() && analysis != null && analysis.isDone()
                        && analysis.join().failure() == null && analysis.join().judgement().judgement().characterExit()) {
                    transitions.interrupt(sessionId, InterruptionCode.CHARACTER_EXIT);
                    return;
                }
                Instant now = clock.instant();
                if (live.scene.trustV3() && n < com.aiscam.antiscam.simulation.rules.SimulationRuleset.maxRounds(session)) {
                    prepareScammer(live, n + 1, dbLines(sessionId));
                }
                if (round.getReadingUntil() != null && round.getReadingUntil().isAfter(now)) {
                    if (live.readingTimer != null) live.readingTimer.cancel(false);
                    live.readingTimer = scheduler.schedule(() -> changed(sessionId), round.getReadingUntil());
                    return;
                }
                if (n < com.aiscam.antiscam.simulation.rules.SimulationRuleset.maxRounds(session)) {
                    transitions.createNextRound(sessionId, n);
                }
            }
        }
    }

    /** Saves a prepared line, writes its temp audio and marks it PRESENTING; handles failures per category. */
    private boolean savePresent(Live live, int n, SpeakerRole role, PreparedLine prepared) {
        if (prepared.line() == null) { transitions.interrupt(live.id, prepared.failure()); return false; }
        Long messageId;
        try {
            messageId = transitions.saveMessage(live.id, n, role, prepared.line(), prepared.action(), prepared.note(),
                    prepared.claimedRefundYuan(), prepared.fundActions());
        } catch (com.aiscam.antiscam.agent.FundsUnavailableException e) {
            if (!live.scene.trustV3()) {
                transitions.interrupt(live.id, InterruptionCode.FUNDS_EXHAUSTED);
                return false;
            }
            // V3.5 never ends because the victim cannot pay: the reply is regenerated without a payment action.
            logFailure(live.id, n, "MESSAGE_SAVE_FUNDS_" + role, e);
            var agentRole = role == SpeakerRole.SCAMMER ? AgentRole.SCAMMER : AgentRole.VICTIM;
            if (live.attempts.get(agentRole + ":" + n).get() > MAX_REGENERATIONS) {
                transitions.interrupt(live.id, InterruptionCode.DIALOGUE_FAILED);
            } else {
                if (role == SpeakerRole.SCAMMER) live.scammer.remove(n);
                else live.victim.remove(n);
                changed(live.id);
            }
            return false;
        } catch (AgentCallException e) {
            logFailure(live.id, n, "MESSAGE_SAVE_" + role, e);
            var agentRole = role == SpeakerRole.SCAMMER ? AgentRole.SCAMMER : AgentRole.VICTIM;
            if (live.attempts.get(agentRole + ":" + n).get() > MAX_REGENERATIONS) {
                transitions.interrupt(live.id, InterruptionCode.DIALOGUE_FAILED);
            } else {
                if (role == SpeakerRole.SCAMMER) live.scammer.remove(n);
                else {
                    live.victim.remove(n);
                    var oldAnalysis = live.analysis.remove(n);
                    if (oldAnalysis != null) oldAnalysis.complete(new PreparedAnalysis(null, InterruptionCode.DIALOGUE_FAILED));
                }
                changed(live.id);
            }
            return false;
        }
        if (messageId == null) return false;
        if (role == SpeakerRole.VICTIM && live.scene.trustV3() && !live.analysis.containsKey(n)) {
            var analysis = new CompletableFuture<PreparedAnalysis>();
            live.analysis.put(n, analysis);
            analysis.whenComplete((result, error) -> changed(live.id));
            prepareFinalAnalysis(live, n);
        }
        if (role == SpeakerRole.VICTIM && live.fundsV2 && !live.scene.trustV3()) prepareFinalAnalysis(live, n);
        return present(live, messageId, prepared);
    }

    private boolean present(Live live, long messageId, PreparedLine prepared) {
        if (prepared.audio() == null) { transitions.interrupt(live.id, InterruptionCode.TTS_FAILED); return false; }
        try {
            audio.write(live.id, messageId, prepared.audio());
        } catch (RuntimeException e) {
            logFailure(live.id, 0, "AUDIO_WRITE", e);
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
        if (n > com.aiscam.antiscam.simulation.rules.SimulationRuleset.maxRounds(requireSession(live.id))
                || live.scammer.containsKey(n)) return;
        var future = CompletableFuture.supplyAsync(() -> {
            ScammerTurn turn;
            try {
                turn = retry(live, feedback -> runner.scammer(live.scene, history, n, remaining(live), live.nextAttempt(AgentRole.SCAMMER, n), transitions.fundsContext(live.id),
                        live.waitingV1 ? waiting.facts(live.id,true,false) : "", feedback, live.scammerGuidance.getOrDefault(n, "")));
            } catch (AgentCallException e) {
                return PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED);
            }
            return new PreparedLine(turn.line(), turn.action(), turn.intent(),
                    live.adminTest && n > 1 ? null : synthesize(live, live.scammerVoice, turn.tone(), turn.line()), null, turn.claimedRefundYuan(), turn.fundActions());
        }, io).exceptionally(e -> {
            logFailure(live.id, n, "SCAMMER_PREPARE", e);
            Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
            return PreparedLine.failed(cause instanceof DataAccessException
                    ? InterruptionCode.PERSISTENCE_FAILED : InterruptionCode.DIALOGUE_FAILED);
        });
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
        // V3.5 settles the whole round only after both lines present, so it never pre-creates an analysis future or
        // an early next-round preparation here.
        var analysisFuture = live.scene.trustV3() ? null : new CompletableFuture<PreparedAnalysis>();
        live.victim.put(n, victimFuture);
        if (analysisFuture != null) live.analysis.put(n, analysisFuture);
        io.execute(() -> {
            try {
                if (live.scene.trustV1() && !live.scene.trustV3()) {
                    try {
                        retry(live, feedback -> {
                            var candidates = runner.event(live.scene, saved, n, remaining(live), live.nextAttempt(AgentRole.EVENT, n), transitions.fundsContext(live.id),
                                    trustEvents.priorEventFacts(live.id,n)
                                            + (live.waitingV1 ? "\n" + waiting.facts(live.id,false,true) : ""), feedback);
                            return recordEvents(live, n, candidates);
                        });
                    } catch (RuntimeException e) {
                        logFailure(live.id, n, "EVENT", e);
                        victimFuture.complete(PreparedLine.failed(InterruptionCode.ANALYSIS_FAILED));
                        completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
                        return;
                    }
                }
                if (live.waitingV1) waiting.beforeVictim(live.id,n);
                prepareVictim(live, n, history, scammerLine, prior, victimFuture, analysisFuture);
            } catch (RuntimeException e) {
                logFailure(live.id, n, "VICTIM_PREPARE", e);
                victimFuture.complete(PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED));
                completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
            }
        });
        victimFuture.whenComplete((r, e) -> changed(live.id));
        if (analysisFuture == null) return;
        analysisFuture.whenComplete((r, e) -> changed(live.id));
        CompletableFuture.allOf(victimFuture, analysisFuture).whenComplete((ignored, e) -> {
            if (e != null) return;
            PreparedAnalysis r = analysisFuture.join();
            if (r.failure() == null && r.judgement().judgement().candidateOutcome() == null && !r.judgement().judgement().characterExit()
                    && n < com.aiscam.antiscam.simulation.rules.SimulationRuleset.maxRounds(requireSession(live.id))) {
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

    private SimulationSession requireSession(long sessionId) {
        return sessions.findById(sessionId).orElseThrow();
    }

    private static void completeAnalysis(CompletableFuture<PreparedAnalysis> future, PreparedAnalysis result) {
        if (future != null) future.complete(result);
    }

    private List<com.aiscam.antiscam.simulation.rules.TrustRules.ScoredEvent> recordEvents(
            Live live, int n, List<TrustEventCandidate> candidates) {
        var round = rounds.findBySessionIdAndRoundNo(live.id, n).orElseThrow();
        var message = messages.findByRoundIdAndSpeakerRole(round.getId(), SpeakerRole.SCAMMER).orElseThrow();
        for (int reload = 0; reload < 2; reload++) {
            var session = sessions.findById(live.id).orElseThrow();
            if (!session.isRunning() || session.getCurrentRoundNo() != n) throw new IllegalStateException("Event round no longer active");
            try {
                var result = trustEvents.record(live.id, n, message.getId(), session.getStateVersion(), candidates);
                if (result != null) return result;
            } catch (IllegalArgumentException e) {
                throw new AgentCallException(AgentCallException.Category.ANALYSIS, true, "event evidence rejected: " + e.getMessage());
            }
            // Playback receipts can advance the version while the source message stays unchanged.
        }
        throw new IllegalStateException("Event state changed repeatedly before commit");
    }

    private void prepareVictim(Live live, int n, List<DialogueLine> history, DialogueLine scammerLine, PriorFacts prior,
            CompletableFuture<PreparedLine> victimFuture, CompletableFuture<PreparedAnalysis> analysisFuture) {
        var currentFunds = transitions.fundsContext(live.id);
        if (live.scene.v12() && !live.scene.trustV3() && currentFunds.availableBalanceYuan() == 0
                && currentFunds.releasableReserveYuan() == 0) {
            victimFuture.complete(PreparedLine.failed(InterruptionCode.FUNDS_EXHAUSTED));
            completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.FUNDS_EXHAUSTED));
            return;
        }
        VictimTurn accepted = null;
        ValidatedJudgement judged = null;
        for (int regeneration = 0; regeneration <= MAX_REGENERATIONS && accepted == null; regeneration++) {
            boolean regenerate = regeneration > 0;
            VictimTurn candidate;
            try {
                candidate = retry(live, feedback -> runner.victim(live.scene, history, scammerLine, n, remaining(live), regenerate,
                        live.nextAttempt(AgentRole.VICTIM, n), transitions.fundsContext(live.id),
                        live.scene.trustV1() ? trustEvents.visibleFacts(live.id, n)
                                + (live.waitingV1 ? "\n"+waiting.facts(live.id,false,false) : "") : "", feedback,
                        live.scene.trustV2() ? trustEvents.scoreBeforeRound(live.id, n)
                                : currentFunds.gate() == null ? 0 : currentFunds.gate().score()));
            } catch (com.aiscam.antiscam.agent.FundsUnavailableException e) {
                if (!live.scene.trustV3()) {
                    victimFuture.complete(PreparedLine.failed(InterruptionCode.FUNDS_EXHAUSTED));
                    completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.FUNDS_EXHAUSTED));
                    return;
                }
                // V1.4: an unaffordable payment is a rejected reply, not the end of the session.
                log.info("Simulation {} round {} victim payment rejected by funds; retrying without payment", live.id, n);
                judged = null;
                continue;
            } catch (AgentCallException e) {
                victimFuture.complete(PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED));
                completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.DIALOGUE_FAILED));
                return;
            }
            if (n >= OutcomeRules.MIN_ROUNDS || live.scene.trustV2() || live.scene.trustV3()) { accepted = candidate; break; }
            // Rounds 1-4: preserve validated trust-v1 goals and decisive-counterevidence exits.
            List<DialogueLine> lines = withVictim(history, scammerLine, n, candidate);
            try {
                judged = retry(live, feedback -> runner.narrator(live.scene, lines, n, prior, remaining(live), live.nextAttempt(AgentRole.NARRATOR, n), transitions.fundsContext(live.id), claimed(live, n), true, candidate.fundActions(), live.waitingV1 ? waiting.facts(live.id,false,true) : "", feedback));
            } catch (AgentCallException e) {
                victimFuture.complete(PreparedLine.failed(InterruptionCode.ANALYSIS_FAILED));
                completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
                return;
            }
            if (judged.judgement().candidateOutcome() != null && !(live.scene.trustV1()
                    && com.aiscam.antiscam.agent.AgentOutputValidator.earlyException(judged.judgement(), n))) {
                log.info("Simulation {} round {} candidate victim reply discarded by minimum-round precheck (regeneration {})",
                        live.id, n, regeneration + 1);
                judged = null;
                continue;
            }
            accepted = candidate;
        }
        if (accepted == null) {
            victimFuture.complete(PreparedLine.failed(InterruptionCode.DIALOGUE_FAILED));
            completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.DIALOGUE_FAILED));
            return;
        }
        VictimTurn victim = accepted;
        if (live.fundsV2) {
            // Final narration starts only after the victim message and its ledger entries commit.
        } else if (judged != null) {
            completeAnalysis(analysisFuture, new PreparedAnalysis(judged, null));
        } else {
            List<DialogueLine> lines = withVictim(history, scammerLine, n, victim);
            io.execute(() -> {
                try {
                    var j = retry(live, feedback -> runner.narrator(live.scene, lines, n, prior, remaining(live), live.nextAttempt(AgentRole.NARRATOR, n), transitions.fundsContext(live.id), claimed(live, n), true, victim.fundActions(), live.waitingV1 ? waiting.facts(live.id,false,true) : "", feedback));
                    completeAnalysis(analysisFuture, new PreparedAnalysis(j, null));
                } catch (RuntimeException e) {
                    logFailure(live.id, n, "NARRATOR_PREPARE", e);
                    completeAnalysis(analysisFuture, new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
                }
            });
        }
        victimFuture.complete(new PreparedLine(victim.line(), victim.action(), victim.psychology(),
                synthesize(live, live.victimVoice, victim.tone(), victim.line()), null, null, victim.fundActions()));
    }

    private Integer claimed(Live live, int n) {
        var future = live.scammer.get(n);
        return future != null && future.isDone() ? future.join().claimedRefundYuan() : null;
    }

    private void prepareFinalAnalysis(Live live, int n) {
        prepareFinalAnalysis(live, n, "");
    }

    private int remainingAnalysisAttempts(Live live, int n) {
        return properties.getLlm().getTransientRetries() + 1
                - live.finalAnalysisCalls.computeIfAbsent(n, ignored -> new AtomicInteger()).get();
    }

    private void prepareFinalAnalysis(Live live, int n, String validationFeedback) {
        var future = live.analysis.get(n);
        io.execute(() -> {
            var events = live.scene.trustV3() ? CompletableFuture.supplyAsync(() ->
                    retry(live, feedback -> runner.event(live.scene, dbLines(live.id), n, remaining(live),
                            live.nextAttempt(AgentRole.EVENT, n), transitions.fundsContext(live.id),
                            trustEvents.priorEventFacts(live.id, n)
                                    + (live.waitingV1 ? "\n" + waiting.facts(live.id, false, true) : ""), feedback)), io)
                    : CompletableFuture.completedFuture(List.<TrustEventCandidate>of());
            try {
                var judgement = retry(live, feedback -> {
                    live.finalAnalysisCalls.get(n).incrementAndGet();
                    return runner.narrator(live.scene, dbLines(live.id), n, priorFacts(live.id, n),
                            remaining(live), live.nextAttempt(AgentRole.NARRATOR, n), transitions.fundsContext(live.id), claimed(live, n),
                            false, List.of(), live.waitingV1 ? waiting.facts(live.id,false,true) : "", feedback);
                }, remainingAnalysisAttempts(live, n), validationFeedback);
                future.complete(new PreparedAnalysis(judgement, events.join(), null));
            } catch (RuntimeException e) {
                logFailure(live.id, n, "NARRATOR_FINAL", e);
                future.complete(new PreparedAnalysis(null, InterruptionCode.ANALYSIS_FAILED));
            } finally {
                events.cancel(true);
            }
        });
    }

    private static List<DialogueLine> withVictim(List<DialogueLine> history, DialogueLine scammerLine, int n, VictimTurn v) {
        List<DialogueLine> lines = new ArrayList<>(history);
        lines.add(scammerLine);
        lines.add(new DialogueLine(n, SpeakerRole.VICTIM, v.line(), v.action(), v.psychology()));
        return lines;
    }

    private static void logFailure(long sessionId, int roundNo, String stage, Throwable error) {
        Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                ? error.getCause() : error;
        // Unexpected exception messages may contain SQL or provider data; log location, not their payload.
        Object detail = cause instanceof AgentCallException ? cause.getMessage()
                : cause.getStackTrace().length == 0 ? "unknown location" : cause.getStackTrace()[0];
        log.warn("Simulation {} round {} stage={} failed type={} detail={}", sessionId, roundNo, stage,
                cause.getClass().getSimpleName(), detail);
    }

    /** Transparent retries of unpublished content within the remaining budget; results never leave the area. */
    <T> T retry(Live live, Function<String, T> call) {
        return retry(live, call, properties.getLlm().getTransientRetries() + 1, "");
    }

    private <T> T retry(Live live, Function<String, T> call, int attempts, String initialFeedback) {
        AgentCallException last = null;
        for (int i = 0; i < attempts; i++) {
            if (!live.alive || remaining(live) <= 0) break;
            try { return call.apply(last == null ? initialFeedback : last.retryFeedback()); }
            catch (AgentCallException e) {
                last = e;
                if (!e.retryable() || i + 1 >= attempts) {
                    log.info("Simulation {} agent output rejected ({}), no further retry", live.id, e.getMessage());
                    break;
                }
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
                log.info("Simulation {} synthesis failed, attempt {}, reason={}", live.id, i + 1, e.getMessage());
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
        boolean trustV1 = session.getEventPromptId() != null;
        if (trustV1) {
            var event = prompts.findById(session.getEventPromptId()).orElseThrow();
            templates.put(event.getId(), event.getTemplateContent());
        }
        var scenario = snapshots.read(session.getScenarioSnapshot());
        var scammer = snapshots.read(session.getScammerSnapshot());
        live.scene = new AgentScene(ScenarioCode.valueOf(scenario.path("scenario_code").asText()), scenario.path("name").asText(),
                scenario.path("description").asText(), snapshots.scammerText(session.getScammerSnapshot()),
                trustV1 ? snapshots.victimVisibleText(session.getVictimSnapshot()) : snapshots.victimText(session.getVictimSnapshot()), scammer.path("name").asText(),
                templates.get(session.getScammerPromptId()), templates.get(session.getVictimPromptId()),
                templates.get(session.getNarratorPromptId()), trustV1 ? snapshots.scammerVisibleText(session.getVictimSnapshot()) : snapshots.victimPublicText(session.getVictimSnapshot()),
                java.util.Set.of("PRD-V3.3", "PRD-V3.4", "PRD-V3.5").contains(snapshots.read(session.getRuntimeSnapshot()).path("ruleset_version").asText()),
                trustV1 ? templates.get(session.getEventPromptId()) : null, trustV1, ConfigurationSnapshots.trustV2(session),
                ConfigurationSnapshots.trustV3(session), snapshots.sceneRuleText(session.getSceneRuleSnapshot()));
        live.waitingV1 = "waiting-v1".equals(snapshots.read(session.getRuntimeSnapshot()).path("waiting_ruleset_version").asText());
        live.adminTest = session.isAdminTest();
        live.fundsV2 = java.util.Set.of("funds-v2", "funds-v3").contains(snapshots.read(session.getRuntimeSnapshot()).path("fund_ruleset_version").asText());
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
        var session = sessions.findById(live.id).filter(SimulationSession::isRunning).orElse(null);
        if (session == null) return 0L;
        // No business deadline in V1.4: the remaining budget is only this step's technical model timeout.
        if (session.getDeadlineAt() == null) return properties.getLlm().getCallTimeoutMs();
        return DeadlineCalculator.remainingMs(session, clock.instant());
    }

    // ------------------------------------------------------------------ deadline & connection

    private void scheduleDeadline(Live live, SimulationSession session) {
        if (live.deadlineTimer != null) live.deadlineTimer.cancel(false);
        Instant next = DeadlineCalculator.nextCheck(session);
        if (next == null) return;
        live.deadlineTimer = scheduler.schedule(() -> lanes.run(live.id, () -> transitions.convergeIfExpired(live.id)), next);
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
