package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.service.TrustEventService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole-round trust-v3 settlement (Phase 1, Task 2). The round is settled from both saved formal messages at once, a
 * wrong owner or a duplicated order rejects the entire batch before anything is written, and an empty event round is
 * still settled so the saved analysis remains the marker. The events, score and counterevidence state share the
 * caller's transaction.
 */
class RoundTrustV3FlowTest {
    private static final long SESSION = 7L;
    private static final int ROUND = 2;
    private static final long ROUND_ID = 10L;
    private static final String SCAMMER_LINE = "这条链接可以退回本金，金额5000元";
    private static final String VICTIM_LINE = "我先核实一下这个日期";

    private final SimulationSessionRepository sessions = mock(SimulationSessionRepository.class);
    private final SimulationRoundRepository rounds = mock(SimulationRoundRepository.class);
    private final SimulationMessageRepository messages = mock(SimulationMessageRepository.class);
    private final SimulationTrustEventRepository events = mock(SimulationTrustEventRepository.class);
    private final FundFlowRepository funds = mock(FundFlowRepository.class);
    private final TrustEventService service = new TrustEventService(sessions, rounds, messages, events, funds, null);

    private final SimulationSession session = mock(SimulationSession.class);
    private final SimulationMessage scammer = mock(SimulationMessage.class);
    private final SimulationMessage victim = mock(SimulationMessage.class);

    private void saved(String content) {
        when(session.isRunning()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(ROUND);
        when(session.getInitialTrustScore()).thenReturn(50);
        when(session.getCurrentTrustScore()).thenReturn(50);
        when(session.getTrustPositiveFactor()).thenReturn(java.math.BigDecimal.ONE);
        when(session.getTrustNegativeFactor()).thenReturn(java.math.BigDecimal.ONE);
        when(sessions.lockById(SESSION)).thenReturn(Optional.of(session));
        var round = mock(SimulationRound.class);
        when(round.getId()).thenReturn(ROUND_ID);
        when(rounds.findBySessionIdAndRoundNo(SESSION, ROUND)).thenReturn(Optional.of(round));
        when(scammer.getSpeakerRole()).thenReturn(SpeakerRole.SCAMMER);
        when(scammer.getId()).thenReturn(11L);
        when(scammer.getContent()).thenReturn(SCAMMER_LINE);
        when(victim.getSpeakerRole()).thenReturn(SpeakerRole.VICTIM);
        when(victim.getId()).thenReturn(12L);
        when(victim.getContent()).thenReturn(VICTIM_LINE);
        when(messages.findByRoundId(ROUND_ID)).thenReturn(List.of(scammer, victim));
        when(events.findBySessionIdOrderByIdAsc(SESSION)).thenReturn(List.of());
        when(funds.findBySessionIdOrderBySequenceNoAsc(SESSION)).thenReturn(List.of());
    }

    private static TrustEventCandidate candidate(TrustEventType type, SpeakerRole speaker, int order, String quote) {
        return new TrustEventCandidate(type, "explanation:费用", quote, EvidenceSource.CONTENT, List.of(), null,
                "first-" + order, null, speaker, order);
    }

    @Test
    void bothSavedMessagesMaySupplyEvidenceAndAnEmptyRoundIsStillSettled() {
        saved(SCAMMER_LINE);
        assertEquals(List.of(), service.recordRoundV3(SESSION, ROUND, List.of()));
        verify(events, never()).save(any());
        verify(session, never()).applyTrustScore(anyInt());
        verify(session).applyCounterevidence(false);
        verify(session).bumpVersion();

        var scammerEvent = candidate(TrustEventType.NEW_EFFECTIVE_EXPLANATION, SpeakerRole.SCAMMER, 1, "金额5000元");
        var victimEvent = candidate(TrustEventType.INDEPENDENT_DOUBT, SpeakerRole.VICTIM, 1, "我先核实一下");
        var scored = service.recordRoundV3(SESSION, ROUND, List.of(scammerEvent, victimEvent));

        assertEquals(2, scored.size());
        assertEquals(53, scored.get(0).scoreAfter(), "the scammer explanation adds three points from the round start");
        assertEquals(50, scored.get(1).scoreAfter(), "the victim's independent doubt is scored in the same round");
        verify(events, times(2)).save(any(SimulationTrustEvent.class));
        verify(session).applyTrustScore(50);
    }

    @Test
    void anEventQuotingTheOtherRoleOrAnOlderRoundRejectsTheWholeBatch() {
        saved(SCAMMER_LINE);
        var valid = candidate(TrustEventType.NEW_EFFECTIVE_EXPLANATION, SpeakerRole.SCAMMER, 1, "金额5000元");
        var wrongOwner = candidate(TrustEventType.PROFESSIONAL_EXPLANATION, SpeakerRole.VICTIM, 1, "金额5000元");

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.recordRoundV3(SESSION, ROUND, List.of(valid, wrongOwner)));
        assertTrue(error.getMessage().contains("declared speaker"));
        verify(events, never()).save(any());
        verify(session, never()).applyTrustScore(anyInt());

        var olderRound = candidate(TrustEventType.PROFESSIONAL_EXPLANATION, SpeakerRole.SCAMMER, 1, "上一轮说过的话");
        var stale = assertThrows(IllegalArgumentException.class,
                () -> service.recordRoundV3(SESSION, ROUND, List.of(valid, olderRound)));
        assertTrue(stale.getMessage().contains("declared speaker"));
        verify(events, never()).save(any());
    }

    @Test
    void aDuplicatedOrderForOneSpeakerAlsoRejectsTheWholeBatch() {
        saved(SCAMMER_LINE);
        var first = candidate(TrustEventType.NEW_EFFECTIVE_EXPLANATION, SpeakerRole.SCAMMER, 1, "金额5000元");
        var duplicate = candidate(TrustEventType.PROFESSIONAL_EXPLANATION, SpeakerRole.SCAMMER, 1, "这条链接");

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.recordRoundV3(SESSION, ROUND, List.of(first, duplicate)));
        assertTrue(error.getMessage().contains("Duplicate eventOrder"));
        verify(events, never()).save(any());
        verify(session, never()).applyTrustScore(anyInt());
        verify(session, never()).bumpVersion();
    }

    @Test
    void aFinishedOrAdvancedRoundIsNoLongerSettled() {
        saved(SCAMMER_LINE);
        when(session.isRunning()).thenReturn(false);
        var error = assertThrows(IllegalStateException.class,
                () -> service.recordRoundV3(SESSION, ROUND, List.of()));
        assertTrue(error.getMessage().contains("no longer active"));
        verify(events, never()).save(any());

        when(session.isRunning()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(ROUND + 1);
        assertThrows(IllegalStateException.class, () -> service.recordRoundV3(SESSION, ROUND, List.of()));
        verify(session, never()).bumpVersion();
    }

    @Test
    void theSettlementMustJoinTheCallersTransaction() throws Exception {
        var method = TrustEventService.class.getMethod("recordRoundV3", long.class, int.class, List.class);
        var transaction = method.getAnnotation(Transactional.class);
        assertNotNull(transaction, "recordRoundV3 must join the analysis transaction");
        assertEquals(Propagation.MANDATORY, transaction.propagation(),
                "events, score and counterevidence state cannot commit without the saved analysis");
    }
}
