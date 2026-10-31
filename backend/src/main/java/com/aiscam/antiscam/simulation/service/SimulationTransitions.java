package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.report.rules.ReportEligibility;

import com.aiscam.antiscam.agent.AgentModels.ValidatedJudgement;
import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.agent.AgentOutputValidator;
import com.aiscam.antiscam.simulation.rules.FundRules;
import java.util.List;
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
import com.aiscam.antiscam.simulation.web.dto.InterventionViews.*;
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
    /** V1.4 is the only ruleset new sessions use: PRD-V3.5, trust-v3, snapshot 4, report 4, no business deadline. */
    private static final boolean V14_SESSION = true;

    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final AgentPromptRepository prompts;
    private final FundFlowRepository funds;
    private final TrustPolicyRepository policies;
    private final TrustEventService trustEvents;
    private final WaitingItemService waiting;
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
    private final SimulationScammerDraftRepository drafts;
    private final SimulationInterventionRepository interventions;
    private final InterventionStateAssembler interventionStates;
    private final SceneRuleRepository sceneRules;

    public SimulationTransitions(SimulationSessionRepository sessions, SimulationRoundRepository rounds,
            SimulationMessageRepository messages, AgentPromptRepository prompts, UserRepository users,
            FraudScenarioRepository scenarios, ScammerProfileRepository scammers, VictimProfileRepository victims,
            AvatarStore avatars, ConfigurationSnapshots snapshots, SimulationStateAssembler assembler,
            SimulationEventHub hub, TimingCalibrator calibrator, VoiceMapper voices, SimulationProperties properties,
            ObjectProvider<SimulationSignals> signals, EntityManager em, Clock clock, FundFlowRepository funds,
            TrustPolicyRepository policies, TrustEventService trustEvents, WaitingItemService waiting,
            SimulationScammerDraftRepository drafts, SimulationInterventionRepository interventions,
            InterventionStateAssembler interventionStates, SceneRuleRepository sceneRules) {
        this.drafts = drafts;
        this.interventions = interventions;
        this.interventionStates = interventionStates;
        this.sceneRules = sceneRules;
        this.waiting=waiting;
        this.policies = policies;
        this.trustEvents = trustEvents;
        this.funds = funds;
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
        return start(userId, httpSessionId, req, scenarioId, scammerId, victimId, receivedAt, InterventionMode.NONE);
    }

    public StartResult start(long userId, String httpSessionId, StartRequest req, long scenarioId, long scammerId,
            long victimId, Instant receivedAt, InterventionMode interventionMode) {
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
        var policy = policies.findById(1).orElseThrow(() -> ApiException.dataInconsistent("演绎策略缺失"));
        var trust = victim.scenarioProfile(scenario.getScenarioCode());
        var voicePair = voices.map(scammer.getAge(), scammer.getGender(), victim.getAge(), victim.getGender());
        var session = new SimulationSession(userId, req.startRequestId(), scenarioId, scammerId, victimId,
                snapshots.scenario(scenario), snapshots.scammer(scammer), snapshots.victim(victim, scenario.getScenarioCode()),
                active.get(PromptType.SCAMMER).getId(), active.get(PromptType.VICTIM).getId(),
                active.get(PromptType.NARRATOR).getId(), active.get(PromptType.REPORT).getId(),
                snapshots.write(runtimeSnapshot(voicePair, policy)), calibrated.startedAt(), calibrated.receivedAt(),
                snapshots.write(calibrated.evidenceJson()), victim.getAvailableFundsYuan(), V14_SESSION);
        session.initializeReserveTotals(victim.getGeneralReserveFundsYuan(), victim.getEmergencyReserveFundsYuan());
        session.setInterventionMode(interventionMode);
        // Freeze the enabled rules of this scene once, in the fixed snapshot order; conflicts never pause a run.
        session.freezeSceneRules(snapshots.write(sceneRules
                .findByScenarioCodeAndStatusOrderByVersionNoDescIdAsc(scenario.getScenarioCode().name(), 
                        com.aiscam.antiscam.simulation.domain.SceneRuleStatus.ENABLED)
                .stream().map(r -> new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot(
                        r.getId().toString(), r.getTitle(), r.getRuleText(), r.getVersionNo())).toList()));
        if (!V14_SESSION) session.initializeActiveDuration(properties.getMaxActiveDurationMs());
        session.freezeTrust(active.get(PromptType.EVENT).getId(), trust.getInitialTrustScore(), trust.getPositiveFactor(),
                trust.getNegativeFactor(), trust.getSmallAmountCapYuan(), policy, V14_SESSION);
        session = sessions.saveAndFlush(session);
        State state = assembler.assemble(session);
        hub.publishAfterCommit(new SimulationEvent("session.started", session.getId(), session.getStateVersion(), state));
        long id = session.getId();
        SimulationEventHub.afterCommit(() -> signals.getObject().started(id));
        return new StartResult(true, state);
    }

    private Map<String, Object> runtimeSnapshot(VoiceMapper.VoicePair voicePair, TrustPolicy policy) {
        var llm = properties.getLlm();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema_version", 4);
        m.put("v12_preview", false);
        m.put("fund_ruleset_version", "funds-v3");
        m.put("report_content_schema_version", 4);
        m.put("app_build", "0.1.0-v1.4-phase1");
        m.put("waiting_ruleset_version", "waiting-v1");
        m.put("ruleset_version", "PRD-V3.5");
        m.put("trust_config", Map.of("trust_ruleset_version", "trust-v3", "rounding", "HALF_UP",
                "threshold_accept", policy.getTrustThresholdAccept(), "threshold_small", policy.getTrustThresholdSmall(),
                "threshold_large", policy.getTrustThresholdLarge()));
        m.put("model_config", Map.of("provider", llm.getProvider(), "model", llm.getModel(),
                "temperature", Map.of("scammer", llm.getScammerTemperature(), "victim", llm.getVictimTemperature(),
                        "narrator", llm.getNarratorTemperature(), "report", llm.getReportTemperature()),
                "max_tokens", llm.getMaxTokens()));
        m.put("media_config", Map.of("scammer_voice", voicePair.scammerVoice(), "victim_voice", voicePair.victimVoice(),
                "voice_mapping_version", voicePair.mappingVersion(), "segmentation_version", SentenceSegmenter.VERSION,
                "animation_version", "1", "scene_intro_version", "1", "tts_provider", properties.getTts().getProvider()));
        // V1.4 has no business deadline: only the reading stay and per-step technical timeouts remain.
        m.put("pacing_config", Map.of("min_rounds", 15, "max_rounds", 15,
                "max_regenerations", 2, "line_target_chars", 50, "analysis_target_chars", 40, "scene_intro_max_ms", 15_000,
                "cards_max_ms", 8_000, "reading_ms", READING_MS,
                "llm_call_timeout_ms", properties.getLlm().getCallTimeoutMs()));
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
        var round = rounds.saveAndFlush(new SimulationRound(s.getId(), 1, now, s.getFundsSummary()));
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
        Instant readingUntil = now.plusMillis(READING_MS);
        Instant deadline = DeadlineCalculator.effectiveDeadline(s, now);
        if (deadline != null) readingUntil = min(readingUntil, deadline);
        round.completePresentation(now, readingUntil);
        s.bumpVersion();
        var decision = decide(s, round, false);
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
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo
                || roundNo >= com.aiscam.antiscam.simulation.rules.SimulationRuleset.maxRounds(s)) return false;
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        if (round.getPhase() != RoundPhase.COMPLETE || round.getReadingUntil() == null || round.getReadingUntil().isAfter(now)) return false;
        var next = rounds.saveAndFlush(new SimulationRound(sessionId, roundNo + 1, now, s.getFundsSummary()));
        s.enterRound(roundNo + 1);
        hub.publishAfterCommit(new SimulationEvent("round.started", sessionId, s.getStateVersion(), assembler.roundStarted(s, next)));
        afterCommitChanged(sessionId);
        return true;
    }

    /** Saves a validated role line as PENDING (audio not ready yet). Returns the message id, or null if stale. */
    public Long saveMessage(long sessionId, int roundNo, SpeakerRole role, String content, String action, String internalNote, Integer claimedRefundYuan, List<FundAction> fundActions) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) return null;
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        RoundPhase expected = role == SpeakerRole.SCAMMER ? RoundPhase.SCAMMER_GENERATING : RoundPhase.VICTIM_GENERATING;
        if (round.getPhase() != expected || messages.findByRoundIdAndSpeakerRole(round.getId(), role).isPresent()) return null;
        return saveMessageLocked(s, round, role, content, action, internalNote, claimedRefundYuan, fundActions, roundNo, now);
    }

    /** Shared by the ordinary pipeline and by adopting an administrator's edited draft. */
    private Long saveMessageLocked(SimulationSession s, SimulationRound round, SpeakerRole role, String content,
            String action, String internalNote, Integer claimedRefundYuan, List<FundAction> fundActions, int roundNo,
            Instant now) {
        long sessionId = s.getId();
        var context = fundsContext(s);
        var priorLines = rounds.findBySessionIdOrderByRoundNoAsc(sessionId).stream().filter(r -> r.getRoundNo() < roundNo)
                .flatMap(r -> messages.findByRoundId(r.getId()).stream().map(m -> new DialogueLine(r.getRoundNo(),
                        m.getSpeakerRole(), m.getContent(), m.getActionText(), null))).toList();
        AgentOutputValidator.funds(role, content, action, claimedRefundYuan, fundActions, roundNo, context, priorLines, isV12(s));
        var message = messages.saveAndFlush(new SimulationMessage(round.getId(), role, content, action, internalNote, now));
        if (context != null) {
            var existing = context.existingFlows();
            int nextSequence = existing.isEmpty() ? 1 : existing.getLast().getSequenceNo() + 1;
            var application = FundRules.apply(s.getFundsSummary(), context, roundNo, nextSequence, fundActions);
            int released = s.getReserveReleasedYuan();
            int generalTotal = s.getGeneralReserveTotalYuan() + s.getEmergencyReserveTotalYuan() == 0
                    ? context.reservedFundsTotalYuan() : s.getGeneralReserveTotalYuan();
            int sequence = nextSequence;
            for (var posting : application.postings()) {
                if (context.v3()) {
                    funds.save(new com.aiscam.antiscam.simulation.domain.FundFlow(sessionId, round.getId(), message.getId(), posting, now));
                } else if (posting.type() == FundFlowType.RESERVE_RELEASE) {
                    int general = Math.min(posting.amountYuan(), Math.max(0, generalTotal - released));
                    if (general > 0) {
                        var part = new FundPosting(posting.type(), posting.purpose(), general, sequence++,
                                posting.balanceAfterYuan() - posting.amountYuan() + general, posting.reason(), null);
                        funds.save(new com.aiscam.antiscam.simulation.domain.FundFlow(sessionId, round.getId(), message.getId(), part, now));
                    }
                    int emergency = posting.amountYuan() - general;
                    if (emergency > 0) {
                        var part = new FundPosting(posting.type(), posting.purpose(), emergency, sequence++,
                                posting.balanceAfterYuan(), posting.reason(), null);
                        var flow = new com.aiscam.antiscam.simulation.domain.FundFlow(sessionId, round.getId(), message.getId(), part, now);
                        flow.setReserveTier("EMERGENCY");
                        funds.save(flow);
                    }
                    released += posting.amountYuan();
                } else {
                    var numbered = new FundPosting(posting.type(), posting.purpose(), posting.amountYuan(), sequence++,
                            posting.balanceAfterYuan(), posting.reason(), posting.relatedPaymentId());
                    funds.save(new com.aiscam.antiscam.simulation.domain.FundFlow(sessionId, round.getId(), message.getId(), numbered, now));
                }
            }
            s.applyFunds(application.after(), context.v3());
            round.applyFunds(application);
        }
        round.setPhase(role == SpeakerRole.SCAMMER ? RoundPhase.SCAMMER_PRESENTING : RoundPhase.VICTIM_PRESENTING);
        s.bumpVersion();
        return message.getId();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FundsContext fundsContext(long sessionId) {
        return fundsContext(sessions.findById(sessionId).orElseThrow());
    }

    // ------------------------------------------------------------------ admin review gate (V1.4 Phase 2)

    /**
     * Holds the generated scammer line for review instead of saving it. Only ADMIN_TEST sessions reach this path, and
     * nothing is written to the formal conversation until the administrator adopts a draft.
     */
    public Long saveDraft(long sessionId, int roundNo, String content, String action, String guidance, long adminUserId,
            Integer claimedRefundYuan, List<FundAction> fundActions) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (s == null || !s.isAdminTest()) throw ApiException.notFound();
        if (s.getUserId() != adminUserId) throw ApiException.notFound();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) return null;
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        if (round.getPhase() != RoundPhase.SCAMMER_GENERATING) return null;
        var draft = new SimulationScammerDraft(sessionId, round.getId(), roundNo,
                drafts.maxRevision(round.getId()) + 1, content, action, content, action, guidance, adminUserId);
        draft.setFunds(claimedRefundYuan, fundActions);
        draft = drafts.saveAndFlush(draft);
        round.setPhase(RoundPhase.SCAMMER_REVIEW);
        s.bumpVersion();
        hub.publishAfterCommit(new SimulationEvent("intervention.draft", sessionId, s.getStateVersion(),
                new InterventionDraftEvent(roundNo, ScammerDraft.from(draft, round.getPhase()),
                        (int) interventions.countBySessionIdAndStatus(sessionId, InterventionStatus.ADOPTED))));
        afterCommitChanged(sessionId);
        return draft.getId();
    }

    /** Replaces an active draft in place and bumps its revision. */
    public Long reviseDraft(long sessionId, int roundNo, long draftId, int expectedRevisionNo, String content,
            String action, String guidance) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (s == null || !s.isAdminTest()) throw ApiException.notFound();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) throw ApiException.staleDraft();
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        if (round.getPhase() != RoundPhase.SCAMMER_REVIEW) throw ApiException.staleDraft();
        var draft = drafts.findActiveByRoundId(round.getId()).orElseThrow(ApiException::staleDraft);
        if (draft.getId() != draftId) throw ApiException.staleDraft();
        if (draft.getRevisionNo() != expectedRevisionNo) throw ApiException.draftRevisionConflict();
        try {
            AgentOutputValidator.funds(SpeakerRole.SCAMMER, content, action, draft.getClaimedRefundYuan(),
                    draft.getFundActions(), roundNo, fundsContext(s), List.of(), isV12(s));
        } catch (com.aiscam.antiscam.agent.AgentCallException e) {
            throw ApiException.field("content", "CONTENT_REJECTED", "改写与当前资金动作不一致，请重新生成以同步更新话术和资金动作");
        }
        draft.revise(content, action, guidance, expectedRevisionNo);
        drafts.flush();
        s.bumpVersion();
        hub.publishAfterCommit(new SimulationEvent("intervention.draft", sessionId, s.getStateVersion(),
                new InterventionDraftEvent(roundNo, ScammerDraft.from(draft, round.getPhase()),
                        (int) interventions.countBySessionIdAndStatus(sessionId, InterventionStatus.ADOPTED))));
        return draft.getId();
    }

    /** Marks a reviewed draft as discarded so the administrator can ask for a new generation. */
    public void discardDraft(long sessionId, int roundNo, long draftId, int expectedRevisionNo, String guidance) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (s == null || !s.isAdminTest()) throw ApiException.notFound();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) throw ApiException.staleDraft();
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        if (round.getPhase() != RoundPhase.SCAMMER_REVIEW) throw ApiException.staleDraft();
        var draft = drafts.findActiveByRoundId(round.getId()).orElseThrow(ApiException::staleDraft);
        if (draft.getId() != draftId) throw ApiException.staleDraft();
        if (draft.getRevisionNo() != expectedRevisionNo) throw ApiException.draftRevisionConflict();
        draft.discard();
        round.setPhase(RoundPhase.SCAMMER_GENERATING);
        drafts.flush();
        s.bumpVersion();
        long id = s.getId();
        SimulationEventHub.afterCommit(() -> signals.getObject().draftDiscarded(id, roundNo, guidance));
    }

    public record AdoptResult(Long messageId, ScammerDraft draft, InterventionRecord record) {}

    /**
     * Commits the adopted line as the round's formal scammer message. Re-checks the draft id and revision under the
     * session lock, so a late request can never overwrite a newer draft or advance the victim.
     */
    public AdoptResult adoptDraft(long sessionId, int roundNo, long draftId, int expectedRevisionNo, String content,
            String action, String note) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (s == null || !s.isAdminTest()) throw ApiException.notFound();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) throw ApiException.staleDraft();
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        if (round.getPhase() != RoundPhase.SCAMMER_REVIEW) throw ApiException.staleDraft();
        var draft = drafts.findActiveByRoundId(round.getId()).orElseThrow(ApiException::staleDraft);
        if (draft.getId() != draftId) throw ApiException.staleDraft();
        if (draft.getRevisionNo() != expectedRevisionNo) throw ApiException.draftRevisionConflict();

        String finalContent = content == null || content.isBlank() ? draft.getDraftContent() : content.strip();
        String finalAction = content == null || content.isBlank() ? draft.getDraftAction() : action;
        Long messageId = saveMessageLocked(s, round, SpeakerRole.SCAMMER, finalContent, finalAction,
                draft.getGuidanceText(), draft.getClaimedRefundYuan(), draft.getFundActions(), roundNo, now);
        draft.adopt();
        SimulationIntervention record = null;
        var original = drafts.findFirstByRoundIdOrderByRevisionNoAsc(round.getId()).orElse(draft);
        if (!finalContent.equals(original.getOriginalContent())
                || !java.util.Objects.equals(finalAction, original.getOriginalAction())) {
            record = new SimulationIntervention(sessionId, round.getId(), roundNo,
                    draft.getId(), snapshots.read(s.getScenarioSnapshot()).path("scenario_code").asText(), s.getUserId(),
                    original.getOriginalContent(), original.getOriginalAction(), finalContent, finalAction,
                    note == null ? draft.getGuidanceText() : note,
                    InterventionMode.ADMIN_TEST);
            record.setPreviousContext(rounds.findBySessionIdAndRoundNo(sessionId, roundNo - 1)
                    .map(previous -> messages.findByRoundId(previous.getId()).stream()
                            .map(m -> (m.getSpeakerRole() == SpeakerRole.SCAMMER ? "骗子：" : "受害者：") + m.getContent())
                            .collect(java.util.stream.Collectors.joining("\n"))).orElse("首轮，暂无上一轮对话"));
            // The formal message now exists: the record is adopted and waits for the knowledge base (option A).
            record.link(messageId);
            record = interventions.saveAndFlush(record);
        }
        s.bumpVersion();
        long id = s.getId();
        SimulationEventHub.afterCommit(() -> signals.getObject().draftAdopted(id, roundNo));
        return new AdoptResult(messageId, ScammerDraft.from(draft, round.getPhase()),
                record == null ? null : InterventionRecord.from(record));
    }

    private FundsContext fundsContext(SimulationSession s) {
        String version = snapshots.read(s.getRuntimeSnapshot()).path("fund_ruleset_version").asText();
        if (!java.util.Set.of("funds-v2", "funds-v3").contains(version)) return null;
        if (version.equals("funds-v3")) return new FundsContext(
                ScenarioCode.valueOf(snapshots.read(s.getScenarioSnapshot()).path("scenario_code").asText()),
                s.getAvailableBalanceYuan(), s.getGeneralReserveTotalYuan() + s.getEmergencyReserveTotalYuan(),
                s.getReserveReleasedYuan(), s.getTotalPaidYuan(), s.getTotalRefundedYuan(),
                funds.findBySessionIdOrderBySequenceNoAsc(s.getId()), s.getTotalRewardedYuan(),
                s.getGeneralReserveTotalYuan(), s.getEmergencyReserveTotalYuan(),
                s.getGeneralReserveReleasedYuan(), s.getEmergencyReserveReleasedYuan(),
                new com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate(s.getCurrentTrustScore(), s.getTrustThresholdAccept(),
                        s.getTrustThresholdSmall(), s.getTrustThresholdLarge(), s.getTrustSmallCapYuan()));
        return new FundsContext(ScenarioCode.valueOf(snapshots.read(s.getScenarioSnapshot()).path("scenario_code").asText()),
                s.getAvailableBalanceYuan(), snapshots.read(s.getVictimSnapshot()).path("reserved_funds_yuan").asInt(),
                s.getReserveReleasedYuan(), s.getTotalPaidYuan(), s.getTotalRefundedYuan(),
                funds.findBySessionIdOrderBySequenceNoAsc(s.getId()));
    }

    /** Temp audio is written: PENDING → PRESENTING, then publish {@code message.saved}. */    public boolean markPresenting(long sessionId, long messageId) {
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
        return saveAnalysis(sessionId, roundNo, validated, null);
    }

    /**
     * V1.4 entry: trust-v3 always settles the whole round through this overload, even with an empty event list.
     * Events, score, counterevidence state and the saved analysis commit in this one short transaction; the saved
     * analysis is the settlement marker, so a duplicate or concurrent callback writes nothing.
     */
    public boolean saveAnalysis(long sessionId, int roundNo, ValidatedJudgement validated,
            List<com.aiscam.antiscam.agent.AgentModels.TrustEventCandidate> roundTrustEvents) {
        var s = sessions.lockById(sessionId).orElse(null);
        Instant now = now();
        if (!runnable(s, now) || s.getCurrentRoundNo() != roundNo) return false;
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo).orElseThrow();
        boolean trustV3 = ConfigurationSnapshots.trustV3(s);
        // Legacy sessions keep their existing re-save semantics; trust-v3 uses the saved analysis as the settlement
        // marker, so a duplicate or concurrent callback writes nothing.
        if (round.getPhase() != RoundPhase.ANALYZING || (trustV3 && round.hasAnalysis())) return false;
        Map<String, Long> ids = new HashMap<>();
        for (var r : rounds.findBySessionIdOrderByRoundNoAsc(sessionId)) {
            for (var m : messages.findByRoundId(r.getId())) ids.put(r.getRoundNo() + ":" + m.getSpeakerRole(), m.getId());
        }
        var j = validated.judgement();
        if (!trustV3 && !ConfigurationSnapshots.trustV2(s) && roundNo < OutcomeRules.MIN_ROUNDS && j.candidateOutcome() != null
                && !(s.getEventPromptId() != null && AgentOutputValidator.earlyException(j, roundNo))) {
            throw new IllegalStateException("Early outcome requires a validated goal, executed fraud followed by a stop, or decisive evidence and a stop");
        }
        if (trustV3) {
            this.trustEvents.recordRoundV3(sessionId, roundNo, roundTrustEvents == null ? List.of() : roundTrustEvents);
        } else if (s.getEventPromptId() != null && !j.attitudeEvents().isEmpty()) {
            var message = messages.findByRoundIdAndSpeakerRole(round.getId(), SpeakerRole.VICTIM).orElseThrow();
            trustEvents.record(sessionId, roundNo, message.getId(), s.getStateVersion(), j.attitudeEvents());
        }
        if (!j.waitingUpdates().isEmpty()) {
            var message=messages.findByRoundIdAndSpeakerRole(round.getId(),SpeakerRole.VICTIM).orElseThrow();
            waiting.recordVictim(s,roundNo,message,j.waitingUpdates());
        }
        int risk = s.getCurrentTrustScore() == null ? j.riskScore()
                : com.aiscam.antiscam.simulation.rules.TrustRiskRules.score(s.getCurrentTrustScore(), j.riskScore(), j.actualFraudOccurred(), j.cooperationStopped());
        String riskReason = s.getCurrentTrustScore() == null ? j.riskReason()
                : com.aiscam.antiscam.simulation.rules.TrustRiskRules.reason(j.actualFraudOccurred(), j.cooperationStopped(), j.riskReason());
        round.saveAnalysis(j.fraudStage(), j.speechAnalysis(), j.methodAnalysis(), j.methodShort(), j.intentShort(),
                j.victimPsychology(), risk, riskReason, j.actualFraudOccurred(), j.goalAchieved(), j.cooperationStopped(),
                EvidenceJson.write(validated.mergedEvidence(), ids), j.candidateOutcome(), j.candidateReason(), now);
        s.bumpVersion();
        hub.publishAfterCommit(new SimulationEvent("analysis.saved", sessionId, s.getStateVersion(),
                new AnalysisSaved(roundNo, assembler.analysis(s, round))));
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
            if (com.aiscam.antiscam.simulation.rules.SimulationRuleset.isV35(s)) {
                // V1.4 restores the reading stay that was left when the pause started, without a 300 s cap.
                Instant readingUntil = round.getReadingUntil();
                long remainingStay = readingUntil == null ? 0 : Math.max(0, readingUntil.toEpochMilli() - pausedAt.toEpochMilli());
                if (remainingStay > 0) round.setReadingUntil(now.plusMillis(remainingStay));
            } else if (round.getReadingUntil() != null && round.getReadingUntil().isAfter(pausedAt)) {
                round.shiftReading(credit);
            }
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

    private boolean isV12(SimulationSession s) {
        return java.util.Set.of("PRD-V3.3", "PRD-V3.4", "PRD-V3.5").contains(snapshots.read(s.getRuntimeSnapshot()).path("ruleset_version").asText());
    }

    private OutcomeRules.Decision decide(SimulationSession s, SimulationRound round, boolean expired) {
        if (com.aiscam.antiscam.simulation.rules.SimulationRuleset.isV35(s)) {
            // V1.4 always runs 15 rounds; the model's candidate is ignored and the program decides the verdict.
            return OutcomeRules.decideV35(round.getRoundNo(),
                    s.getCurrentTrustScore() == null ? 0 : s.getCurrentTrustScore(), s.getActualLossYuan());
        }
        var candidate = round.getCandidateOutcome();
        if (ConfigurationSnapshots.trustV2(s)) return OutcomeRules.decideV2(round.getRoundNo(), candidate,
                candidate == null ? null : round.getCandidateReason(), expired, round.isActualFraudOccurred());
        // Achieving a fraud goal records a fact; an active conversation can still escalate.
        if (s.getEventPromptId() != null && round.isGoalAchieved() && !round.isCooperationStopped()
                && smallPaymentsOnly(s) && (long) s.getAvailableBalanceYuan() + s.getGeneralReserveTotalYuan() - s.getGeneralReserveReleasedYuan()
                        + s.getEmergencyReserveTotalYuan() - s.getEmergencyReserveReleasedYuan() > 0) candidate = null;
        return isV12(s) ? OutcomeRules.decide(round.getRoundNo(), candidate, candidate == null ? null : round.getCandidateReason(),
                expired, round.isActualFraudOccurred(), earlyException(s, round))
                : OutcomeRules.decide(round.getRoundNo(), round.getCandidateOutcome(), round.getCandidateReason(), expired);
    }

    private boolean smallPaymentsOnly(SimulationSession s) {
        long unrecovered = 0;
        for (var flow : funds.findBySessionIdOrderBySequenceNoAsc(s.getId())) {
            if (flow.getFlowType() == FundFlowType.REFUND) unrecovered -= flow.getAmountYuan();
            if (flow.getFlowType() == FundFlowType.RESERVE_RELEASE && "EMERGENCY".equals(flow.getReserveTier())) return false;
            if (flow.getFlowType() == FundFlowType.PAYMENT) {
                unrecovered += flow.getAmountYuan();
                if (flow.getAmountYuan() > s.getTrustSmallCapYuan() || unrecovered > s.getTrustSmallCapYuan()) return false;
            }
        }
        return true;
    }

    private boolean earlyException(SimulationSession s, SimulationRound round) {
        if (s.getEventPromptId() == null || round.getRoundNo() >= OutcomeRules.MIN_ROUNDS) return false;
        if (round.isActualFraudOccurred() && (round.isGoalAchieved() || round.isCooperationStopped())) return true;
        var evidence = EvidenceJson.read(round.getEvidenceJson());
        return round.isCooperationStopped() && evidence.stream().anyMatch(e -> e.roundNo() == round.getRoundNo()
                && e.factCode() == com.aiscam.antiscam.simulation.rules.FactCode.DECISIVE_COUNTEREVIDENCE_FOUND)
                && evidence.stream().anyMatch(e -> e.roundNo() == round.getRoundNo()
                && e.factCode() == com.aiscam.antiscam.simulation.rules.FactCode.COOPERATION_STOPPED);
    }

    private void convergeDeadline(SimulationSession s, Instant now) {
        var round = s.getCurrentRoundNo() == 0 ? null
                : rounds.findBySessionIdAndRoundNo(s.getId(), s.getCurrentRoundNo()).orElse(null);
        if (round != null && round.getPhase() == RoundPhase.COMPLETE) {
            finishLocked(s, round, decide(s, round, true), now);
        } else {
            interruptLocked(s, InterruptionCode.TIME_LIMIT, now);
        }
    }

    /** Formal finish and the PENDING report row in the same transaction; finished is published before the last reading. */
    private void finishLocked(SimulationSession s, SimulationRound round, OutcomeRules.Decision decision, Instant now) {
        s.settlePause(now);
        boolean v35 = com.aiscam.antiscam.simulation.rules.SimulationRuleset.isV35(s);
        if (!v35) round.capReading(s.getDeadlineAt());
        if (!v35 && decision.limitReason() == null && (earlyException(s, round)
                || ConfigurationSnapshots.trustV2(s) && round.getRoundNo() < OutcomeRules.MIN_ROUNDS)) s.allowEarlyFinish();
        if (v35) {
            s.finishV35(decision.outcome(), decision.reason(),
                    s.getCurrentTrustScore() == null ? 0 : s.getCurrentTrustScore(), now);
        } else {
            s.finish(decision.outcome(), decision.reason(), decision.limitReason(), now);
        }
        em.persist(SimulationReport.pending(s.getId(), now, snapshots.read(s.getRuntimeSnapshot()).path("report_content_schema_version").asInt(1)));
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
        if (ReportEligibility.isBusinessEnd(s) && ReportEligibility.supportsBusinessReport(s)
                && ReportEligibility.isEligible(s,rounds.countBySessionIdAndPhase(s.getId(),RoundPhase.COMPLETE)))
            em.persist(SimulationReport.pending(s.getId(),now,snapshots.read(s.getRuntimeSnapshot()).path("report_content_schema_version").asInt(1)));
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
