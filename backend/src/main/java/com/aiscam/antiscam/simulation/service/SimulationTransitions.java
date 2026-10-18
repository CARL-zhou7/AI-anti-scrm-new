package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.agent.AgentModels.ValidatedJudgement;
import com.aiscam.antiscam.auth.domain.UserRepository;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.avatar.AvatarStore;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.media.SentenceSegmenter;
import com.aiscam.antiscam.media.VoiceMapper;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.engine.SimulationSignals;
import com.aiscam.antiscam.simulation.rules.DeadlineCalculator;
import com.aiscam.antiscam.simulation.rules.OutcomeRules;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.*;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.aiscam.antiscam.sse.SimulationEventHub.SimulationEvent;
import com.aiscam.antiscam.timing.TimingCalibrator;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import static com.aiscam.antiscam.common.time.UtcTime.format;

/**
 * Every write to the simulation tables (DB design §7.3). Each public method is one short transaction: lock the
 * session row, re-check status/round/phase, write, bump {@code state_version}, and register after-commit SSE
 * publication. No model, TTS or other external call or wait ever happens in here.
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SimulationTransitions {
    public static final String APPLIED = "APPLIED", ALREADY_APPLIED = "ALREADY_APPLIED", IGNORED_TERMINAL = "IGNORED_TERMINAL";
    private static final long READING_MS = 5_000;

    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final AgentPromptRepository prompts;
    private final UserRepository users;
    private final FraudScenarioRepository scenarios;
    private final ScammerProfileRepository scammers;
    private final VictimProfileRepository victims;
    private final AvatarStore avatars;
    private final ConfigurationSnapshots snapshots;
    private final SimulationStateAssembler assembler;
    private final SimulationEventHub hub;
    private final TimingCalibrator calibrator;
    private final VoiceMapper voices;
    private final SimulationProperties properties;
    private final ObjectProvider<SimulationSignals> signals;
    private final EntityManager em;
    private final Clock clock;

    public SimulationTransitions(SimulationSessionRepository sessions, SimulationRoundRepository rounds,
            SimulationMessageRepository messages, AgentPromptRepository prompts, UserRepository users,
            FraudScenarioRepository scenarios, ScammerProfileRepository scammers, VictimProfileRepository victims,
            AvatarStore avatars, ConfigurationSnapshots snapshots, SimulationStateAssembler assembler,
            SimulationEventHub hub, TimingCalibrator calibrator, VoiceMapper voices, SimulationProperties properties,
            ObjectProvider<SimulationSignals> signals, EntityManager em, Clock clock) {
        this.sessions = sessions;
        this.rounds = rounds;
        this.messages = messages;
        this.prompts = prompts;
        this.users = users;
        this.scenarios = scenarios;
        this.scammers = scammers;
        this.victims = victims;
        this.avatars = avatars;
        this.snapshots = snapshots;
        this.assembler = assembler;
        this.hub = hub;
        this.calibrator = calibrator;
        this.voices = voices;
        this.properties = properties;
        this.signals = signals;
        this.em = em;
        this.clock = clock;
    }

    public record StartResult(boolean created, State state) {}

    // ------------------------------------------------------------------ start (DB design §7.1)

    public StartResult start(long userId, String httpSessionId, StartRequest req, long scenarioId, long scammerId,
            long victimId, Instant receivedAt) {
        users.lockById(userId).orElseThrow(ApiException::notFound);
        var existing = sessions.findByUserIdAndStartRequestId(userId, req.startRequestId());
        if (existing.isPresent()) {
            var s = existing.get();
            if (s.getScenarioId() != scenarioId || s.getScammerProfileId() != scammerId || s.getVictimProfileId() != victimId) {
                throw ApiException.idempotencyConflict();
            }
            return new StartResult(false, assembler.assemble(s));
        }
        sessions.findFirstByUserIdAndStatus(userId, SimulationStatus.RUNNING)
                .ifPresent(active -> { throw ApiException.activeSimulationExists(active.getId().toString()); });
        var calibrated = calibrator.calibrate(req.timing(), httpSessionId, userId, receivedAt);
        var scenario = scenarios.shareLockById(scenarioId).orElseThrow(ApiException::notFound);
        var scammer = scammers.shareLockById(scammerId).orElseThrow(ApiException::notFound);
        var victim = victims.shareLockById(victimId).orElseThrow(ApiException::notFound);
        if (!scammer.isEnabled() || !victim.isEnabled()) throw ApiException.profileUnavailable();
        for (String key : new String[]{scammer.getAvatarKey(), scammer.getSpeakingAvatarKey(), victim.getAvatarKey(),
                victim.getSpeakingAvatarKey()}) {
            try { avatars.load(key); } catch (ApiException e) { throw ApiException.dataInconsistent("记录状态异常，请稍后重试"); }
        }
        Map<PromptType, AgentPrompt> active = new EnumMap<>(PromptType.class);
        for (AgentPrompt p : prompts.findActive()) {
            if (active.put(p.getPromptType(), p) != null) throw ApiException.dataInconsistent("记录状态异常，请稍后重试");
        }
        if (active.size() != PromptType.values().length) throw ApiException.dataInconsistent("记录状态异常，请稍后重试");
        var voicePair = voices.map(scammer.getAge(), scammer.getGender(), victim.getAge(), victim.getGender());
        var session = sessions.saveAndFlush(new SimulationSession(userId, req.startRequestId(), scenarioId, scammerId, victimId,
                snapshots.scenario(scenario), snapshots.scammer(scammer), snapshots.victim(victim),
                active.get(PromptType.SCAMMER).getId(), active.get(PromptType.VICTIM).getId(),
                active.get(PromptType.NARRATOR).getId(), active.get(PromptType.REPORT).getId(),
                snapshots.write(runtimeSnapshot(voicePair)), calibrated.startedAt(), calibrated.receivedAt(),
                snapshots.write(calibrated.evidenceJson())));
        State state = assembler.assemble(session);
        hub.publishAfterCommit(new SimulationEvent("session.started", session.getId(), session.getStateVersion(), state));
        long id = session.getId();
        SimulationEventHub.afterCommit(() -> signals.getObject().started(id));
        return new StartResult(true, state);
    }

    private Map<String, Object> runtimeSnapshot(VoiceMapper.VoicePair voicePair) {
        var llm = properties.getLlm();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema_version", 1);
        m.put("app_build", "0.1.0-phase4");
        m.put("ruleset_version", "PRD-V3.2");
        m.put("model_config", Map.of("provider", llm.getProvider(), "model", llm.getModel(),
                "temperature", Map.of("scammer", llm.getScammerTemperature(), "victim", llm.getVictimTemperature(),
                        "narrator", llm.getNarratorTemperature(), "report", llm.getReportTemperature()),
                "max_tokens", llm.getMaxTokens()));
        m.put("media_config", Map.of("scammer_voice", voicePair.scammerVoice(), "victim_voice", voicePair.victimVoice(),
                "voice_mapping_version", voicePair.mappingVersion(), "segmentation_version", SentenceSegmenter.VERSION,
                "animation_version", "1", "scene_intro_version", "1", "tts_provider", properties.getTts().getProvider()));
        m.put("pacing_config", Map.of("min_rounds", OutcomeRules.MIN_ROUNDS, "max_rounds", OutcomeRules.MAX_ROUNDS,
                "max_regenerations", 2, "line_target_chars", 50, "analysis_target_chars", 40, "scene_intro_max_ms", 15_000,
                "cards_max_ms", 8_000, "reading_ms", READING_MS));
        m.put("report_attempt_timeout_ms", properties.getReportAttemptTimeoutMs());
        return m;
    }

    // ------------------------------------------------------------------ presentation receipts (API-17/18/19)

    public EffectResult receiptOpening(long userId, long sessionId, PresentationReceipt receipt) {
        var s = lockOwned(sessionId, userId);
        boolean completed = "COMPLETED".equals(receipt.status());
        if (!s.isRunning()) {
            boolean same = completed ? s.getCurrentRoundNo() >= 1
                    : s.getCurrentRoundNo() == 0 && s.getInterruptionCode() == InterruptionCode.MEDIA_FAILED;
            return result(same ? ALREADY_APPLIED : IGNORED_TERMINAL, s);
        }
        if (s.getCurrentRoundNo() >= 1) {
            if (completed) return result(ALREADY_APPLIED, s);
            throw ApiException.presentationConflict();
        }
        Instant now = now();
        if (DeadlineCalculator.isExpired(s, now)) { convergeDeadline(s, now); return result(IGNORED_TERMINAL, s); }
        if (!completed) { interruptLocked(s, InterruptionCode.MEDIA_FAILED, now); return result(APPLIED, s); }
        var round = rounds.saveAndFlush(new SimulationRound(s.getId(), 1, now));
        s.enterRound(1);
        if (!s.isPaused()) {
            hub.publishAfterCommit(new SimulationEvent("round.started", s.getId(), s.getStateVersion(), assembler.roundStarted(s, round)));
        }
        afterCommitChanged(s.getId());
        return result(APPLIED, s);
    }

    public EffectResult receiptMessage(long userId, long sessionId, long messageId, PresentationReceipt receipt) {
        var s = lockOwned(sessionId, userId);
        var message = messages.findById(messageId).orElseThrow(ApiException::notFound);
        var round = rounds.findById(message.getRoundId()).orElseThrow(ApiException::notFound);
        if (round.getSessionId() != s.getId()) throw ApiException.notFound();
        boolean completed = "COMPLETED".equals(receipt.status());
        var status = message.getPresentationStatus();
        boolean same = completed ? status == PresentationStatus.COMPLETED : status == PresentationStatus.FAILED;
        boolean opposite = completed ? status == PresentationStatus.FAILED : status == PresentationStatus.COMPLETED;
        if (!s.isRunning()) return result(same ? ALREADY_APPLIED : IGNORED_TERMINAL, s);
        if (same) return result(ALREADY_APPLIED, s);
        if (opposite) throw ApiException.presentationConflict();
        Instant now = now();
        if (DeadlineCalculator.isExpired(s, now)) { convergeDeadline(s, now); return result(IGNORED_TERMINAL, s); }
        RoundPhase expected = message.getSpeakerRole() == SpeakerRole.SCAMMER ? RoundPhase.SCAMMER_PRESENTING : RoundPhase.VICTIM_PRESENTING;
        if (round.getRoundNo() != s.getCurrentRoundNo() || round.getPhase() != expected || status != PresentationStatus.PRESENTING) {
            throw ApiException.stageConflict();
        }
        if (!completed) {
            message.fail(receipt.errorCode());
            interruptLocked(s, InterruptionCode.MEDIA_FAILED, now);
            return result(APPLIED, s);
        }
        message.complete(now);
        round.setPhase(message.getSpeakerRole() == SpeakerRole.SCAMMER ? RoundPhase.VICTIM_GENERATING : RoundPhase.ANALYZING);
        s.bumpVersion();
        afterCommitChanged(s.getId());
        return result(APPLIED, s);
    }

    public EffectResult receiptAnalysis(long userId, long sessionId, int roundNo, PresentationReceipt receipt) {
        var s = lockOwned(sessionId, userId);
        if (roundNo > s.getCurrentRoundNo()) {
            if (!s.isRunning()) return result(IGNORED_TERMINAL, s);
            throw ApiException.stageConflict();
        }
        var round = rounds.findBySessionIdAndRoundNo(s.getId(), roundNo).orElseThrow(ApiException::notFound);
        boolean completed = "COMPLETED".equals(receipt.status());
        boolean presented = round.getAnalysisPresentedAt() != null;
        if (!s.isRunning()) return result(completed && presented ? ALREADY_APPLIED : IGNORED_TERMINAL, s);
        if (presented) {
            if (completed) return result(ALREADY_APPLIED, s);
            throw ApiException.presentationConflict();
        }
        Instant now = now();
        if (DeadlineCalculator.isExpired(s, now)) { convergeDeadline(s, now); return result(IGNORED_TERMINAL, s); }
        if (roundNo != s.getCurrentRoundNo() || round.getPhase() != RoundPhase.ANALYSIS_PRESENTING) throw ApiException.stageConflict();
        if (!completed) { interruptLocked(s, InterruptionCode.MEDIA_FAILED, now); return result(APPLIED, s); }
        Instant readingUntil = min(now.plusMillis(READING_MS), DeadlineCalculator.effectiveDeadline(s, now));
        round.completePresentation(now, readingUntil);
        s.bumpVersion();
        var decision = OutcomeRules.decide(roundNo, round.getCandidateOutcome(), round.getCandidateReason(), false);
        if (decision != null) {
            finishLocked(s, round, decision, now);
        } else {
            hub.publishAfterCommit(new SimulationEvent("round.reading", s.getId(), s.getStateVersion(),
                    new RoundReading(roundNo, format(readingUntil), "NEXT_ROUND")));
            afterCommitChanged(s.getId());
        }
        return result(APPLIED, s);
    }

    // ------------------------------------------------------------------ engine-driven steps

    /** Inserts round n+1 after the reading pause of round n expired. Returns false when not (yet) applicable. */
    public boolean createNextRound(long sessionId, int roundNo) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo || roundNo >= OutcomeRules.MAX_ROUNDS) return false;
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        if (round.getPhase() != RoundPhase.COMPLETE || round.getReadingUntil() == null || round.getReadingUntil().isAfter(now)) return false;
        var next = rounds.saveAndFlush(new SimulationRound(sessionId, roundNo + 1, now));
        s.enterRound(roundNo + 1);
        hub.publishAfterCommit(new SimulationEvent("round.started", sessionId, s.getStateVersion(), assembler.roundStarted(s, next)));
        afterCommitChanged(sessionId);
        return true;
    }

    /** Saves a validated role line as PENDING (audio not ready yet). Returns the message id, or null if stale. */
    public Long saveMessage(long sessionId, int roundNo, SpeakerRole role, String content, String action, String internalNote) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) return null;
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        RoundPhase expected = role == SpeakerRole.SCAMMER ? RoundPhase.SCAMMER_GENERATING : RoundPhase.VICTIM_GENERATING;
        if (round.getPhase() != expected || messages.findByRoundIdAndSpeakerRole(round.getId(), role).isPresent()) return null;
        var message = messages.saveAndFlush(new SimulationMessage(round.getId(), role, content, action, internalNote, now));
        round.setPhase(role == SpeakerRole.SCAMMER ? RoundPhase.SCAMMER_PRESENTING : RoundPhase.VICTIM_PRESENTING);
        s.bumpVersion();
        return message.getId();
    }

    /** Temp audio is written: PENDING → PRESENTING, then publish {@code message.saved}. */
    public boolean markPresenting(long sessionId, long messageId) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (!runnable(s, now)) return false;
        var message = messages.findById(messageId).orElseThrow();
        var round = rounds.findById(message.getRoundId()).orElseThrow();
        if (round.getSessionId() != sessionId || round.getRoundNo() != s.getCurrentRoundNo()
                || message.getPresentationStatus() != PresentationStatus.PENDING) return false;
        message.markPresenting();
        s.bumpVersion();
        hub.publishAfterCommit(new SimulationEvent("message.saved", sessionId, s.getStateVersion(),
                assembler.message(s, message, round.getRoundNo())));
        return true;
    }

    /** Saves the whole analysis group at once (phase ANALYZING → ANALYSIS_PRESENTING), then {@code analysis.saved}. */
    public boolean saveAnalysis(long sessionId, int roundNo, ValidatedJudgement validated) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) return false;
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        if (round.getPhase() != RoundPhase.ANALYZING) return false;
        Map<String, Long> ids = new HashMap<>();
        for (var r : rounds.findBySessionIdOrderByRoundNoAsc(sessionId)) {
            for (var m : messages.findByRoundId(r.getId())) ids.put(r.getRoundNo() + ":" + m.getSpeakerRole(), m.getId());
        }
        var j = validated.judgement();
        round.saveAnalysis(j.fraudStage(), j.speechAnalysis(), j.methodAnalysis(), j.victimPsychology(), j.riskScore(),
                j.riskReason(), j.actualFraudOccurred(), j.goalAchieved(), j.cooperationStopped(),
                EvidenceJson.write(validated.mergedEvidence(), ids), j.candidateOutcome(), j.candidateReason(), now);
        s.bumpVersion();
        hub.publishAfterCommit(new SimulationEvent("analysis.saved", sessionId, s.getStateVersion(),
                new AnalysisSaved(roundNo, assembler.analysis(round))));
        return true;
    }

    /** RUNNING → INTERRUPTED with a server-decided code. Returns false if already terminal. */
    public boolean interrupt(long sessionId, InterruptionCode code) {
        var s = sessions.lockById(sessionId).orElse(null);
        if (s == null || !s.isRunning()) return false;
        interruptLocked(s, code, now());
        return true;
    }

    /** Deadline handling: complete-round position → FINISHED (UNDETERMINED/TIME_LIMIT etc.), otherwise TIME_LIMIT interrupt. */
    public boolean convergeIfExpired(long sessionId) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (s == null || !s.isRunning() || !DeadlineCalculator.isExpired(s, now)) return false;
        convergeDeadline(s, now);
        return true;
    }

    // ------------------------------------------------------------------ pause / resume (API-43/44, DB design §7.9)

    public EffectResult pause(long userId, long sessionId) {
        var s = lockOwned(sessionId, userId);
        if (!s.isRunning()) return result(IGNORED_TERMINAL, s);
        if (s.isPaused()) return result(ALREADY_APPLIED, s);
        Instant now = now();
        if (DeadlineCalculator.isExpired(s, now)) { convergeDeadline(s, now); return result(IGNORED_TERMINAL, s); }
        s.pause(now);
        State state = assembler.assemble(s);
        hub.publishAfterCommit(new SimulationEvent("session.paused", sessionId, s.getStateVersion(), state));
        long id = s.getId();
        SimulationEventHub.afterCommit(() -> signals.getObject().pauseChanged(id));
        return new EffectResult(APPLIED, state);
    }

    public EffectResult resume(long userId, long sessionId) {
        var s = lockOwned(sessionId, userId);
        if (!s.isRunning()) return result(IGNORED_TERMINAL, s);
        if (!s.isPaused()) return result(ALREADY_APPLIED, s);
        Instant now = now();
        if (DeadlineCalculator.isExpired(s, now)) { convergeDeadline(s, now); return result(IGNORED_TERMINAL, s); }
        Instant pausedAt = s.getPausedAt();
        long credit = s.settlePause(now);
        if (s.getCurrentRoundNo() > 0) {
            var round = rounds.findBySessionIdAndRoundNo(sessionId, s.getCurrentRoundNo()).orElseThrow();
            if (round.getReadingUntil() != null && round.getReadingUntil().isAfter(pausedAt)) round.shiftReading(credit);
        }
        s.bumpVersion();
        State state = assembler.assemble(s);
        hub.publishAfterCommit(new SimulationEvent("session.resumed", sessionId, s.getStateVersion(), state));
        long id = s.getId();
        SimulationEventHub.afterCommit(() -> { signals.getObject().pauseChanged(id); signals.getObject().changed(id); });
        return new EffectResult(APPLIED, state);
    }

    // ------------------------------------------------------------------ exit (API-20)

    public State exit(long userId, long sessionId) {
        var s = lockOwned(sessionId, userId);
        if (s.isRunning()) interruptLocked(s, InterruptionCode.USER_EXIT, now());
        return assembler.assemble(s);
    }

    // ------------------------------------------------------------------ internals

    private void convergeDeadline(SimulationSession s, Instant now) {
        var round = s.getCurrentRoundNo() == 0 ? null
                : rounds.findBySessionIdAndRoundNo(s.getId(), s.getCurrentRoundNo()).orElse(null);
        if (round != null && round.getPhase() == RoundPhase.COMPLETE) {
            finishLocked(s, round, OutcomeRules.decide(round.getRoundNo(), round.getCandidateOutcome(), round.getCandidateReason(), true), now);
        } else {
            interruptLocked(s, InterruptionCode.TIME_LIMIT, now);
        }
    }

    /** Formal finish and the PENDING report row in the same transaction; finished is published before the last reading. */
    private void finishLocked(SimulationSession s, SimulationRound round, OutcomeRules.Decision decision, Instant now) {
        s.settlePause(now);
        round.capReading(s.getDeadlineAt());
        s.finish(decision.outcome(), decision.reason(), decision.limitReason(), now);
        em.persist(SimulationReport.pending(s.getId(), now));
        em.flush();
        State state = assembler.assemble(s);
        hub.publishAfterCommit(new SimulationEvent("session.finished", s.getId(), s.getStateVersion(), state));
        hub.publishAfterCommit(new SimulationEvent("round.reading", s.getId(), s.getStateVersion(),
                new RoundReading(round.getRoundNo(), format(round.getReadingUntil()), "REPORT")));
        long id = s.getId();
        SimulationEventHub.afterCommit(() -> signals.getObject().terminated(id, false));
    }

    private void interruptLocked(SimulationSession s, InterruptionCode code, Instant now) {
        s.settlePause(now);
        s.interrupt(code, now);
        em.flush();
        hub.publishAfterCommit(new SimulationEvent("session.interrupted", s.getId(), s.getStateVersion(), assembler.assemble(s)));
        long id = s.getId();
        SimulationEventHub.afterCommit(() -> signals.getObject().terminated(id, true));
    }

    private boolean runnable(SimulationSession s, Instant now) {
        if (s == null || !s.isRunning() || s.isPaused()) return false;
        if (DeadlineCalculator.isExpired(s, now)) { convergeDeadline(s, now); return false; }
        return true;
    }

    private SimulationSession lockOwned(long sessionId, long userId) {
        var s = sessions.lockById(sessionId).orElseThrow(ApiException::notFound);
        if (s.getUserId() != userId) throw ApiException.notFound();
        return s;
    }

    private EffectResult result(String effect, SimulationSession s) {
        em.flush();
        return new EffectResult(effect, assembler.assemble(s));
    }

    private void afterCommitChanged(long sessionId) {
        SimulationEventHub.afterCommit(() -> signals.getObject().changed(sessionId));
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MILLIS); }

    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
}
