package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.service.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrustV2EventTest {
    @Test void positiveExplanationDoesNotSuppressVictimsNewDoubtOnSameSubject() {
        var sessions=mock(SimulationSessionRepository.class);
        var rounds=mock(SimulationRoundRepository.class);
        var messages=mock(SimulationMessageRepository.class);
        var events=mock(SimulationTrustEventRepository.class);
        var now=Instant.now();
        var session=new SimulationSession(1,"request",1,1,1,"{}","{}","{}",1,2,3,4,
                "{\"trust_config\":{\"trust_ruleset_version\":\"trust-v2\"}}",now,now,"{}",1000);
        session.enterRound(1);
        ReflectionTestUtils.setField(session,"currentTrustScore",100);
        ReflectionTestUtils.setField(session,"trustPositiveFactor",BigDecimal.ONE);
        ReflectionTestUtils.setField(session,"trustNegativeFactor",BigDecimal.ONE);
        var saved=new ArrayList<SimulationTrustEvent>();
        saved.add(new SimulationTrustEvent(1,1,19L,new com.aiscam.antiscam.simulation.rules.TrustRules.ScoredEvent(
                TrustEventType.NEW_EFFECTIVE_EXPLANATION,"保证金",3,BigDecimal.ONE,0,100,100),"CONTENT","补200元保证金","新费用"));
        when(events.findBySessionIdOrderByIdAsc(1L)).thenReturn(saved);
        when(events.save(any())).thenAnswer(call->{saved.add(call.getArgument(0));return call.getArgument(0);});
        when(sessions.lockById(1L)).thenReturn(Optional.of(session));
        when(rounds.findById(10L)).thenReturn(Optional.of(new SimulationRound(1,1,now)));
        when(messages.findById(20L)).thenReturn(Optional.of(new SimulationMessage(10,SpeakerRole.VICTIM,
                "费用一次次加，我不踏实，暂时不补",null,"",now)));
        var service=new TrustEventService(sessions,rounds,messages,events,mock(FundFlowRepository.class),mock(WaitingItemService.class));
        var doubt=new TrustEventCandidate(TrustEventType.INDEPENDENT_DOUBT,"保证金","费用一次次加，我不踏实",EvidenceSource.CONTENT);
        service.record(1,1,20,session.getStateVersion(),List.of(doubt));
        assertEquals(99,session.getCurrentTrustScore());
        service.record(1,1,20,session.getStateVersion(),List.of(doubt));
        assertEquals(99,session.getCurrentTrustScore());
    }
    @Test void newProgressOnSameSubjectScoresButReplayAndCategoryChangesDoNot() {
        var sessions = mock(SimulationSessionRepository.class);
        var rounds = mock(SimulationRoundRepository.class);
        var messages = mock(SimulationMessageRepository.class);
        var events = mock(SimulationTrustEventRepository.class);
        var funds = mock(FundFlowRepository.class);
        var now = Instant.now();
        var session = new SimulationSession(1,"request",1,1,1,"{}","{}","{}",1,2,3,4,
                "{\"trust_config\":{\"trust_ruleset_version\":\"trust-v2\"}}",now,now,"{}",1000);
        session.enterRound(1);
        ReflectionTestUtils.setField(session,"currentTrustScore",50);
        ReflectionTestUtils.setField(session,"trustPositiveFactor",BigDecimal.ONE);
        ReflectionTestUtils.setField(session,"trustNegativeFactor",BigDecimal.ONE);
        var message = new SimulationMessage(10,SpeakerRole.SCAMMER,"具体说明结算时间。补充手续费承担方。之前说法有矛盾。",null,"内部",now);
        when(sessions.lockById(1L)).thenReturn(Optional.of(session));
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(messages.findById(20L)).thenReturn(Optional.of(message));
        when(rounds.findById(10L)).thenReturn(Optional.of(new SimulationRound(1,1,now)));
        var saved = new ArrayList<SimulationTrustEvent>();
        when(events.findBySessionIdOrderByIdAsc(1L)).thenReturn(saved);
        when(events.save(any())).thenAnswer(call -> { saved.add(call.getArgument(0)); return call.getArgument(0); });
        var service = new TrustEventService(sessions,rounds,messages,events,funds,mock(WaitingItemService.class));
        var ordinary = event(TrustEventType.NEW_EFFECTIVE_EXPLANATION,"到账时间","具体说明结算时间");
        var resolved = event(TrustEventType.DOUBT_RESOLVED,"到账时间","具体说明结算时间");
        service.record(1,1,20,session.getStateVersion(),List.of(ordinary,resolved));
        assertEquals(54,session.getCurrentTrustScore());
        assertEquals(1,saved.size());
        service.record(1,1,20,session.getStateVersion(),List.of(ordinary,resolved,
                event(TrustEventType.PROFESSIONAL_EXPLANATION,"换类型","具体说明结算时间")));
        assertEquals(54,session.getCurrentTrustScore());
        service.record(1,1,20,session.getStateVersion(),List.of(event(TrustEventType.NEW_EFFECTIVE_EXPLANATION,"手续费","补充手续费承担方")));
        assertEquals(57,session.getCurrentTrustScore());
        service.record(1,1,20,session.getStateVersion(),List.of(event(TrustEventType.NEW_CONTRADICTION,"到账时间","之前说法有矛盾")));
        assertEquals(52,session.getCurrentTrustScore());
        assertEquals(50,service.scoreBeforeRound(1,1));
        var missing = new TrustEventCandidate(TrustEventType.NEW_EFFECTIVE_EXPLANATION,"结算","补充手续费承担方",EvidenceSource.CONTENT);
        assertThrows(IllegalArgumentException.class,()->service.record(1,1,20,session.getStateVersion(),List.of(missing)));
        assertEquals(3,saved.size());
        service.record(1,1,20,session.getStateVersion(),List.of(
                event(TrustEventType.NEW_EFFECTIVE_EXPLANATION,"新费用说明","具体说明结算时间。补充手续费承担方"),
                event(TrustEventType.NEW_CONTRADICTION,"新费用改口","补充手续费承担方。之前说法有矛盾")));
        assertEquals(47,session.getCurrentTrustScore());
        assertEquals(4,saved.size());
    }
    private TrustEventCandidate event(TrustEventType type, String progress, String quote) {
        return new TrustEventCandidate(type,"结算",quote,EvidenceSource.CONTENT,List.of(),null,progress);
    }
}
