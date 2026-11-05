package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.AgentOutputValidator;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.rules.TrustRules;
import com.aiscam.antiscam.simulation.rules.TrustRules.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TrustEventService {
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final SimulationTrustEventRepository events;
    private final FundFlowRepository funds;
    private final WaitingItemService waiting;

    public TrustEventService(SimulationSessionRepository sessions, SimulationRoundRepository rounds,
            SimulationMessageRepository messages, SimulationTrustEventRepository events, FundFlowRepository funds, WaitingItemService waiting) {
        this.waiting=waiting;
        this.sessions = sessions;
        this.rounds = rounds;
        this.messages = messages;
        this.events = events;
        this.funds = funds;
    }

    @Transactional(readOnly = true)
    public String visibleFacts(long sessionId, int roundNo) {
        return events.findBySessionIdOrderByIdAsc(sessionId).stream().filter(e -> e.getRoundNo() == roundNo)
                .map(e -> "本轮已知事实：" + e.getEvidenceQuote()).distinct().collect(java.util.stream.Collectors.joining("\n"));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveCounterevidence(long sessionId) {
        var active = new java.util.HashSet<String>();
        for (var event : events.findBySessionIdOrderByIdAsc(sessionId)) {
            if (TrustEventType.DECISIVE_COUNTEREVIDENCE.name().equals(event.getEventType())) active.add(event.getSubjectKey());
            if (TrustEventType.COUNTEREVIDENCE_RESOLVED.name().equals(event.getEventType())) active.remove(event.getSubjectKey());
        }
        return !active.isEmpty();
    }

    @Transactional(readOnly = true)
    public String priorEventFacts(long sessionId, int roundNo) {
        return "此前已记录的事件主题（沿用同一事由的主题键，不重复认定）：\n"
                + events.findBySessionIdOrderByIdAsc(sessionId).stream().filter(e -> e.getRoundNo() < roundNo)
                        .map(e -> e.getEventType() + "；subjectKey=" + e.getSubjectKey() + "；progressKey="
                                + e.getProgressKey() + "；原文=" + e.getEvidenceQuote())
                        .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Transactional(readOnly = true)
    public int scoreBeforeRound(long sessionId, int roundNo) {
        return events.findBySessionIdOrderByIdAsc(sessionId).stream().filter(e -> e.getRoundNo() == roundNo)
                .findFirst().map(SimulationTrustEvent::getScoreBefore)
                .orElseGet(() -> sessions.findById(sessionId).orElseThrow().getCurrentTrustScore());
    }

    private record EventEvidence(RawEvent event, String source, String quote, String progressKey) {
        EventEvidence(RawEvent event, String source, String quote) { this(event, source, quote, event.subjectKey()); }
    }

    private static String progress(TrustEventCandidate event) {
        return event.subjectKey() + ":" + (event.progressKey() == null ? event.subjectKey() : event.progressKey());
    }
    private static String category(TrustEventType type) { return type.positiveExplanation() ? "POSITIVE" : type.name(); }
    private static String quoteKey(String category, String quote) {
        return category + ":" + quote.replaceAll("[\\p{P}\\s]", "");
    }

    /** null means the caller must reload: no rows or score changes were written. */
    public List<ScoredEvent> record(long sessionId, int roundNo, long messageId, long expectedStateVersion,
            List<TrustEventCandidate> candidates) {
        var session = sessions.lockById(sessionId).orElse(null);
        if (session == null || !session.isRunning() || session.getCurrentRoundNo() != roundNo
                || session.getStateVersion() != expectedStateVersion) return null;
        if (session.getCurrentTrustScore() == null) return List.of();
        var message = messages.findById(messageId).orElseThrow(() -> new IllegalArgumentException("Missing event message"));
        var round = rounds.findById(message.getRoundId()).orElseThrow();
        if (round.getSessionId() != sessionId || round.getRoundNo() != roundNo) throw new IllegalArgumentException("Event message belongs to another round");
        boolean attitude = message.getSpeakerRole() == SpeakerRole.VICTIM;
        boolean v2 = ConfigurationSnapshots.trustV2(session);
        for (var candidate : candidates) {
            AgentOutputValidator.trustEvent(candidate, attitude);
            if (!v2 && (candidate.type().v2Only() || candidate.progressKey() != null))
                throw new IllegalArgumentException("Legacy session cannot use v2 trust events");
            if (v2 && !attitude && candidate.progressKey() == null)
                throw new IllegalArgumentException("Trust-v2 event requires progressKey");
            if (!AgentOutputValidator.eventQuoteMatches(candidate,message.getContent(),message.getActionText()))
                throw new IllegalArgumentException("Event quote is not in the message");
        }
        var seen = new HashSet<String>();
        var quotes = new HashSet<String>();
        var coveredSubjects = new HashSet<String>();
        var negativeSubjects = new HashSet<String>();
        for (var prior : events.findBySessionIdOrderByIdAsc(sessionId)) {
            var type = TrustEventType.valueOf(prior.getEventType());
            seen.add(v2 ? category(type) + ":" + prior.getSubjectKey() + ":" + prior.getProgressKey()
                    : prior.getEventType() + ":" + prior.getSubjectKey());
            if (type == TrustEventType.PROMISE_FULFILLED) seen.add(type + ":" + prior.getSubjectKey());
            quotes.add(v2 ? quoteKey(category(type), prior.getEvidenceQuote())
                    : prior.getEventType() + ":" + prior.getEvidenceSource() + ":" + prior.getEvidenceQuote());
            if (prior.getRoundNo() == roundNo) coveredSubjects.add(prior.getSubjectKey());
            if (prior.getRoundNo() == roundNo && type.baseDelta() < 0) negativeSubjects.add(prior.getSubjectKey());
        }
        var accepted = new ArrayList<EventEvidence>();
        var payouts = attitude ? List.<FundFlow>of() : funds.findBySessionIdOrderBySequenceNoAsc(sessionId).stream()
                .filter(f -> java.util.Objects.equals(f.getMessageId(), messageId) && (f.getFlowType() == FundFlowType.REFUND || f.getFlowType() == FundFlowType.REWARD)).toList();
        for (var candidate : candidates) {
            if (v2 && candidate.type().positiveExplanation() && candidates.stream().anyMatch(other ->
                    other.type().baseDelta() < 0 && other.subjectKey().equals(candidate.subjectKey()))) continue;
            if (v2 && candidate.type().positiveExplanation() && candidates.stream().anyMatch(other ->
                    other.type().positiveExplanation() && progress(other).equals(progress(candidate))
                            && other.type().baseDelta() > candidate.type().baseDelta())) continue;
            if (attitude && (v2 && candidate.type() == TrustEventType.INDEPENDENT_DOUBT
                    ? negativeSubjects : coveredSubjects).contains(candidate.subjectKey())) continue;
            if (candidate.type() == TrustEventType.PROMISE_FULFILLED) {
                // Use the model's event position, but only the ledger can establish fulfilment.
                appendPayouts(accepted, seen, payouts);
            } else if (seen.add(v2 ? category(candidate.type()) + ":" + progress(candidate)
                    : candidate.type() + ":" + candidate.subjectKey())
                    && quotes.add(v2 ? quoteKey(category(candidate.type()), candidate.quote())
                    : candidate.type() + ":" + candidate.source() + ":" + candidate.quote())) {
                accepted.add(new EventEvidence(new RawEvent(candidate.type(), candidate.subjectKey()), candidate.source().name(),
                        candidate.quote(), v2 && candidate.progressKey() != null ? candidate.progressKey() : candidate.subjectKey()));
            }
        }
        appendPayouts(accepted, seen, payouts);
        if (!attitude) waiting.recordMaterials(session,roundNo,message,candidates);
        var scored = TrustRules.score(session.getCurrentTrustScore(), session.getTrustPositiveFactor(),
                session.getTrustNegativeFactor(), accepted.stream().map(EventEvidence::event).toList());
        for (int i = 0; i < scored.size(); i++) {
            var evidence = accepted.get(i);
            events.save(new SimulationTrustEvent(sessionId, roundNo, messageId, scored.get(i), evidence.source(), evidence.quote(), evidence.progressKey()));
        }
        session.applyTrustScore(scored.isEmpty() ? session.getCurrentTrustScore() : scored.getLast().scoreAfter());
        session.bumpVersion();
        return scored;
    }

    private static void appendPayouts(List<EventEvidence> accepted, java.util.Set<String> seen, List<FundFlow> payouts) {
        for (var flow : payouts) {
            String key = "fund:" + flow.getId();
            if (seen.add(TrustEventType.PROMISE_FULFILLED + ":" + key)) {
                accepted.add(new EventEvidence(new RawEvent(TrustEventType.PROMISE_FULFILLED, key),
                        "SYSTEM", (flow.getFlowType() == FundFlowType.REWARD ? "报酬实际到账" : "本金实际到账") + flow.getAmountYuan() + "元"));
            }
        }
    }

    // ------------------------------------------------------------------ V1.4 trust-v3 whole-round settlement

    /** Two different pieces of state: a subject can be released and still keep its highest accepted level. */
    public record CounterevidenceState(java.util.Set<String> activeSubjects,
            java.util.Map<String, com.aiscam.antiscam.simulation.rules.CounterevidenceLevel> highestLevels) {}

    @Transactional(readOnly = true)
    public CounterevidenceState counterevidenceState(long sessionId) {
        var active = new java.util.LinkedHashSet<String>();
        var highest = new java.util.LinkedHashMap<String, com.aiscam.antiscam.simulation.rules.CounterevidenceLevel>();
        for (var saved : events.findBySessionIdOrderByIdAsc(sessionId)) {
            var type = TrustEventType.valueOf(saved.getEventType());
            if (type == TrustEventType.DECISIVE_COUNTEREVIDENCE) {
                var level = saved.getCounterevidenceLevel() == null
                        ? com.aiscam.antiscam.simulation.rules.CounterevidenceLevel.PARTIAL
                        : com.aiscam.antiscam.simulation.rules.CounterevidenceLevel.valueOf(saved.getCounterevidenceLevel());
                if (level.strongerThan(highest.get(saved.getSubjectKey()))) {
                    highest.put(saved.getSubjectKey(), level);
                    active.add(saved.getSubjectKey());
                }
            } else if (type == TrustEventType.COUNTEREVIDENCE_RESOLVED) {
                active.remove(saved.getSubjectKey());
            }
        }
        return new CounterevidenceState(java.util.Set.copyOf(active), java.util.Map.copyOf(highest));
    }

    private record AcceptedEvent(TrustEventType type, String subjectKey, String progressKey,
            com.aiscam.antiscam.simulation.rules.CounterevidenceLevel level, Long messageId, String source, String quote) {}

    /**
     * Settles one full round of trust-v3 evidence inside the caller's transaction. Both roles may be cited, quotes
     * must be verbatim from that role's saved message of this round, and a payout only counts once per real ledger
     * row. Returns the scored events in stored order; the empty list still means "settled".
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public List<com.aiscam.antiscam.simulation.rules.TrustV3Rules.ScoredEvent> recordRoundV3(long sessionId,
            int roundNo, List<TrustEventCandidate> candidates) {
        var session = sessions.lockById(sessionId).orElseThrow(() -> new IllegalArgumentException("Missing session"));
        if (!session.isRunning() || session.getCurrentRoundNo() != roundNo)
            throw new IllegalStateException("Trust-v3 round is no longer active");
        if (session.getCurrentTrustScore() == null) return List.of();
        var round = rounds.findBySessionIdAndRoundNo(sessionId, roundNo)
                .orElseThrow(() -> new IllegalArgumentException("Missing round"));
        var byRole = new java.util.EnumMap<SpeakerRole, SimulationMessage>(SpeakerRole.class);
        for (var message : messages.findByRoundId(round.getId())) byRole.put(message.getSpeakerRole(), message);

        var seen = new java.util.HashSet<String>();
        var quotes = new java.util.HashSet<String>();
        for (var saved : events.findBySessionIdOrderByIdAsc(sessionId)) {
            var type = TrustEventType.valueOf(saved.getEventType());
            seen.add(type + ":" + saved.getSubjectKey() + ":" + saved.getProgressKey());
            quotes.add(type + ":" + saved.getEvidenceSource() + ":" + saved.getEvidenceQuote());
        }
        for (var candidate : candidates) {
            AgentOutputValidator.trustEvent(candidate, false, true);
            var message = byRole.get(candidate.speaker());
            if (message == null)
                throw new IllegalArgumentException("Event speaker has no saved message in round " + roundNo);
            if (!AgentOutputValidator.eventQuoteMatches(candidate, message.getContent(), message.getActionText()))
                throw new IllegalArgumentException("Event quote is not in the declared speaker's saved message");
        }
        var orders = new java.util.HashSet<String>();
        for (var candidate : candidates) {
            if (!orders.add(candidate.speaker() + ":" + candidate.eventOrder()))
                throw new IllegalArgumentException("Duplicate eventOrder for the same speaker in one round");
        }

        var accepted = new ArrayList<AcceptedEvent>();
        var payouts = funds.findBySessionIdOrderBySequenceNoAsc(sessionId).stream()
                .filter(f -> f.getFlowType() == FundFlowType.REFUND || f.getFlowType() == FundFlowType.REWARD)
                .filter(f -> f.getMessageId() != null && byRole.values().stream()
                        .anyMatch(m -> m.getId().equals(f.getMessageId())))
                .toList();
        for (var role : List.of(SpeakerRole.SCAMMER, SpeakerRole.VICTIM)) {
            var message = byRole.get(role);
            if (message == null) continue;
            for (var candidate : candidates.stream()
                    .filter(c -> c.speaker() == role)
                    .sorted(java.util.Comparator.comparing(TrustEventCandidate::eventOrder))
                    .toList()) {
                accept(accepted, seen, quotes, candidate, message.getId());
            }
            if (role == SpeakerRole.SCAMMER) appendRoundPayouts(accepted, seen, payouts);
        }

        var state = counterevidenceState(sessionId);
        var scored = com.aiscam.antiscam.simulation.rules.TrustV3Rules.score(session.getInitialTrustScore(),
                session.getCurrentTrustScore(), session.getTrustPositiveFactor(), session.getTrustNegativeFactor(),
                accepted.stream().map(a -> new com.aiscam.antiscam.simulation.rules.TrustV3Rules.V3Event(
                        a.type(), a.subjectKey(), a.progressKey(), a.level())).toList(),
                state.activeSubjects(), state.highestLevels());
        int pointer = 0;
        var stored = new ArrayList<com.aiscam.antiscam.simulation.rules.TrustV3Rules.ScoredEvent>();
        for (var event : scored.events()) {
            while (pointer < accepted.size() && !matches(accepted.get(pointer), event)) pointer++;
            if (pointer >= accepted.size()) throw new IllegalStateException("Scored event has no accepted evidence");
            var evidence = accepted.get(pointer++);
            events.save(new SimulationTrustEvent(sessionId, roundNo, evidence.messageId(), event, evidence.source(), evidence.quote()));
            stored.add(event);
        }
        if (!stored.isEmpty()) session.applyTrustScore(stored.getLast().scoreAfter());
        session.applyCounterevidence(!scored.activeSubjects().isEmpty());
        session.bumpVersion();
        return stored;
    }

    private static boolean matches(AcceptedEvent evidence,
            com.aiscam.antiscam.simulation.rules.TrustV3Rules.ScoredEvent event) {
        return evidence.type() == event.type() && evidence.subjectKey().equals(event.subjectKey())
                && java.util.Objects.equals(evidence.progressKey(), event.progressKey());
    }

    private static void accept(List<AcceptedEvent> accepted, java.util.Set<String> seen, java.util.Set<String> quotes,
            TrustEventCandidate candidate, Long messageId) {
        var type = candidate.type();
        if (type == TrustEventType.PROMISE_FULFILLED) return;
        if (type.positiveInV3() && accepted.stream().anyMatch(other -> other.type().baseDeltaV3() < 0
                && other.subjectKey().equals(candidate.subjectKey()))) return;
        if (type.positiveInV3() && accepted.stream().anyMatch(other -> other.type().positiveInV3()
                && java.util.Objects.equals(other.progressKey(), candidate.progressKey())
                && other.type().baseDeltaV3() > type.baseDeltaV3())) return;
        if (!seen.add(type + ":" + candidate.subjectKey() + ":" + candidate.progressKey())) return;
        if (!quotes.add(type + ":" + candidate.source().name() + ":" + candidate.quote())) return;
        accepted.add(new AcceptedEvent(type, candidate.subjectKey(), candidate.progressKey(),
                candidate.counterevidenceLevel(), messageId, candidate.source().name(), candidate.quote()));
    }

    private static void appendRoundPayouts(List<AcceptedEvent> accepted, java.util.Set<String> seen, List<FundFlow> payouts) {
        for (var flow : payouts) {
            String key = "fund:" + flow.getId();
            if (!seen.add(TrustEventType.PROMISE_FULFILLED + ":" + key + ":" + key)) continue;
            accepted.add(new AcceptedEvent(TrustEventType.PROMISE_FULFILLED, key, key, null, flow.getMessageId(),
                    "SYSTEM", (flow.getFlowType() == FundFlowType.REWARD ? "报酬实际到账" : "本金实际到账") + flow.getAmountYuan() + "元"));
        }
    }
}
