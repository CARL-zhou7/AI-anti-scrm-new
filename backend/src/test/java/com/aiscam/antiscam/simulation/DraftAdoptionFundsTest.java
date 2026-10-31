package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.agent.AgentCallException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.engine.SimulationSignals;
import com.aiscam.antiscam.simulation.service.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class DraftAdoptionFundsTest {
    @Test void adoptingRegenerationPostsRewardAndKeepsFirstOriginalAndPreviousDialogue() {
        runAdoption(100, false);
    }

    @Test void adoptionRejectsCumulativePayoutBeyondThreeHundredBeforeSavingMessage() {
        runAdoption(101, true);
    }

    @SuppressWarnings("unchecked")
    private void runAdoption(int amount, boolean rejected) {
        var sessions = mock(SimulationSessionRepository.class);
        var rounds = mock(SimulationRoundRepository.class);
        var messages = mock(SimulationMessageRepository.class);
        var drafts = mock(SimulationScammerDraftRepository.class);
        var interventions = mock(SimulationInterventionRepository.class);
        var funds = mock(FundFlowRepository.class);
        var session = mock(SimulationSession.class);
        when(session.getId()).thenReturn(1L);
        when(session.getUserId()).thenReturn(7L);
        when(session.isRunning()).thenReturn(true);
        when(session.isAdminTest()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(2);
        when(session.getRuntimeSnapshot()).thenReturn("{\"ruleset_version\":\"PRD-V3.5\",\"fund_ruleset_version\":\"funds-v3\"}");
        when(session.getScenarioSnapshot()).thenReturn("{\"scenario_code\":\"TASK_REBATE\"}");
        when(session.getCurrentTrustScore()).thenReturn(80);
        when(session.getAvailableBalanceYuan()).thenReturn(1200);
        when(session.getTotalRewardedYuan()).thenReturn(200);
        when(session.getFundsSummary()).thenReturn(new FundsSummary(1000,1200,0,0,200,0,0,0,0));
        when(sessions.lockById(1)).thenReturn(Optional.of(session));
        var round = new SimulationRound(1,2,Instant.EPOCH);
        ReflectionTestUtils.setField(round,"id",2L);
        round.setPhase(RoundPhase.SCAMMER_REVIEW);
        when(rounds.findBySessionIdAndRoundNo(1,2)).thenReturn(Optional.of(round));
        var previous = new SimulationRound(1,1,Instant.EPOCH);
        ReflectionTestUtils.setField(previous,"id",1L);
        when(rounds.findBySessionIdAndRoundNo(1,1)).thenReturn(Optional.of(previous));
        when(messages.findByRoundId(1)).thenReturn(List.of(
                new SimulationMessage(1,SpeakerRole.SCAMMER,"先看看流程",null,null,Instant.EPOCH),
                new SimulationMessage(1,SpeakerRole.VICTIM,"流程是什么",null,null,Instant.EPOCH)));
        String line = "已实际发放" + amount + "元";
        var original = new SimulationScammerDraft(1,2,2,0,"最初AI原稿",null,"最初AI原稿",null,null,7);
        var draft = new SimulationScammerDraft(1,2,2,1,line,null,line,null,"调整体验报酬",7);
        ReflectionTestUtils.setField(draft,"id",3L);
        draft.setFunds(amount,List.of(new FundAction(FundFlowType.REWARD,FundPurpose.REWARD,amount,2,
                SpeakerRole.SCAMMER,line,1,null,null,null)));
        when(drafts.findActiveByRoundId(2)).thenReturn(Optional.of(draft));
        when(drafts.findFirstByRoundIdOrderByRevisionNoAsc(2)).thenReturn(Optional.of(original));
        when(messages.saveAndFlush(any())).thenAnswer(inv -> {
            var message = inv.getArgument(0); ReflectionTestUtils.setField(message,"id",9L); return message;
        });
        when(interventions.saveAndFlush(any())).thenAnswer(inv -> {
            var record = inv.getArgument(0); ReflectionTestUtils.setField(record,"id",10L); return record;
        });
        var signals = mock(ObjectProvider.class);
        when(signals.getObject()).thenReturn(mock(SimulationSignals.class));
        var transitions = new SimulationTransitions(sessions,rounds,messages,null,null,null,null,null,null,
                new ConfigurationSnapshots(new ObjectMapper()),null,mock(SimulationEventHub.class),null,null,
                new SimulationProperties(),signals,null,Clock.systemUTC(),funds,null,null,null,drafts,interventions,null,null);
        if (rejected) {
            assertThrows(AgentCallException.class, () -> transitions.adoptDraft(1,2,3,1,null,null,null));
            verify(messages,never()).saveAndFlush(any());
            verify(funds,never()).save(any());
            assertEquals(ScammerDraftStatus.DRAFT,draft.getStatus());
        } else {
            var result = transitions.adoptDraft(1,2,3,1,null,null,null);
            verify(funds).save(argThat(f -> f.getAmountYuan() == 100 && f.getMessageId() == 9L));
            verify(session).applyFunds(argThat(f -> f.totalRewardedYuan() == 300),eq(true));
            assertEquals("最初AI原稿",result.record().originalContent());
            assertEquals(line,result.record().finalContent());
            assertEquals("骗子：先看看流程\n受害者：流程是什么",result.record().previousContext());
            assertEquals("ADOPTED",result.record().status());
        }
    }
}
