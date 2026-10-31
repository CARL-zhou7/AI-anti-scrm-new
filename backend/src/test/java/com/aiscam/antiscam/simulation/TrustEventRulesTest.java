package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.service.TrustEventService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrustEventRulesTest {
    @Test
    void recordsOnceAndRejectsForeignEvidenceOrStaleVersion() {
        var sessions = mock(SimulationSessionRepository.class);
        var rounds = mock(SimulationRoundRepository.class);
        var messages = mock(SimulationMessageRepository.class);
        var events = mock(SimulationTrustEventRepository.class);
        var funds = mock(FundFlowRepository.class);
        Instant now = Instant.now();
        var session = new SimulationSession(1,"request",1,1,1,"{}","{}","{}",1,2,3,4,"{}",now,now,"{}",1000);
        session.enterRound(1);
        ReflectionTestUtils.setField(session,"currentTrustScore",50);
        ReflectionTestUtils.setField(session,"trustPositiveFactor",BigDecimal.ONE);
        ReflectionTestUtils.setField(session,"trustNegativeFactor",BigDecimal.ONE);
        var round = new SimulationRound(1,1,now);
        var message = new SimulationMessage(10,SpeakerRole.SCAMMER,"这是新的说明",null,"内部",now);
        when(sessions.lockById(1L)).thenReturn(Optional.of(session));
        when(messages.findById(20L)).thenReturn(Optional.of(message));
        when(rounds.findById(10L)).thenReturn(Optional.of(round));
        when(funds.findBySessionIdOrderBySequenceNoAsc(1L)).thenReturn(List.of());
        var saved = new ArrayList<SimulationTrustEvent>();
        when(events.findBySessionIdOrderByIdAsc(1L)).thenReturn(saved);
        when(events.save(any())).thenAnswer(call -> { saved.add(call.getArgument(0)); return call.getArgument(0); });
        var service = new TrustEventService(sessions,rounds,messages,events,funds, mock(com.aiscam.antiscam.simulation.service.WaitingItemService.class));
        var candidate = new TrustEventCandidate(TrustEventType.NEW_EFFECTIVE_EXPLANATION,"explanation:规则","新的说明",EvidenceSource.CONTENT);
        service.record(1,1,20,session.getStateVersion(),List.of(candidate));
        service.record(1,1,20,session.getStateVersion(),List.of(candidate));
        assertEquals(1,saved.size());
        assertEquals(53,session.getCurrentTrustScore());
        assertNull(service.record(1,1,20,0,List.of(candidate)));
        var fake = new TrustEventCandidate(candidate.type(),"fake","不存在的原文",EvidenceSource.CONTENT);
        assertThrows(IllegalArgumentException.class,()->service.record(1,1,20,session.getStateVersion(),List.of(fake)));
        ReflectionTestUtils.setField(round,"sessionId",2L);
        assertThrows(IllegalArgumentException.class,()->service.record(1,1,20,session.getStateVersion(),List.of(candidate)));
        assertEquals(53,session.getCurrentTrustScore());
    }
}
