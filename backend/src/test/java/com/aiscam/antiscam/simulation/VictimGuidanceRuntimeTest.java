package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.VictimGuidance;
import com.aiscam.antiscam.simulation.service.*;
import com.aiscam.antiscam.simulation.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VictimGuidanceRuntimeTest {
    @Test void savedDecisionIsIdempotentAndTheSamePermissionReachesTheSaveContext() {
        var sessions = mock(SimulationSessionRepository.class);
        var rounds = mock(SimulationRoundRepository.class);
        var messages = mock(SimulationMessageRepository.class);
        var ledger = mock(FundFlowRepository.class);
        var trust = mock(TrustEventService.class);
        var snapshots = new ConfigurationSnapshots(new ObjectMapper());
        var clock = Clock.fixed(Instant.parse("2026-10-31T00:00:00Z"), ZoneOffset.UTC);
        var service = new SimulationTransitions(sessions, rounds, messages, null, null, null, null, null, null,
                snapshots, null, null, null, null, null, null, null, clock, ledger, null, trust, null, null, null, null, null);
        var session = mock(SimulationSession.class);
        when(session.getId()).thenReturn(7L); when(session.isRunning()).thenReturn(true);
        when(session.getCurrentRoundNo()).thenReturn(9);
        when(session.getRuntimeSnapshot()).thenReturn("{\"fund_ruleset_version\":\"funds-v3\",\"ruleset_version\":\"PRD-V3.5\"}");
        when(session.getScenarioSnapshot()).thenReturn("{\"scenario_code\":\"FAKE_INVESTMENT\"}");
        when(session.getAvailableBalanceYuan()).thenReturn(250);
        when(session.getGeneralReserveTotalYuan()).thenReturn(1000);
        when(session.getEmergencyReserveTotalYuan()).thenReturn(2000);
        when(session.getCurrentTrustScore()).thenReturn(20);
        when(session.getTrustThresholdAccept()).thenReturn(40); when(session.getTrustThresholdSmall()).thenReturn(70);
        when(session.getTrustThresholdLarge()).thenReturn(85); when(session.getTrustSmallCapYuan()).thenReturn(1000);
        when(session.getSceneRuleSnapshot()).thenReturn(snapshots.write(List.of(new SceneRuleSnapshot("4", "拒绝后配合", "接受请求", 1, "VICTIM"))));
        when(sessions.lockById(7L)).thenReturn(Optional.of(session)); when(sessions.findById(7L)).thenReturn(Optional.of(session));
        var r7 = new SimulationRound(7, 7, clock.instant()); var r8 = new SimulationRound(7, 8, clock.instant());
        var r9 = new SimulationRound(7, 9, clock.instant());
        for (var r : List.of(r7, r8, r9)) {
            ReflectionTestUtils.setField(r, "id", (long) r.getRoundNo());
            when(rounds.findBySessionIdAndRoundNo(7, r.getRoundNo())).thenReturn(Optional.of(r));
        }
        var first = new VictimGuidance(7, "4", "4:7", "请转1000元给模拟商户", 1000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", 1, false, false, false, "接受请求");
        var second = new VictimGuidance(8, "4", "4:7", first.requestQuote(), 1000,
                first.purpose(), first.recipient(), 2, false, false, false, "接受请求");
        r7.setVictimGuidanceJson(snapshots.write(first)); r8.setVictimGuidanceJson(snapshots.write(second));
        when(rounds.findBySessionIdOrderByRoundNoAsc(7)).thenReturn(List.of(r7, r8, r9));
        for (var r : List.of(r7, r8)) when(messages.findByRoundId(r.getId())).thenReturn(List.of(
                new SimulationMessage(r.getId(), SpeakerRole.VICTIM, "我暂时不转", null, null, clock.instant())));
        var current = new SimulationMessage(9L, SpeakerRole.SCAMMER, first.requestQuote(), null, null, clock.instant());
        when(messages.findByRoundIdAndSpeakerRole(9, SpeakerRole.SCAMMER)).thenReturn(Optional.of(current));
        when(messages.findByRoundIdAndSpeakerRole(9, SpeakerRole.VICTIM)).thenReturn(Optional.empty());
        when(ledger.findBySessionIdOrderBySequenceNoAsc(7)).thenReturn(List.of());
        var match = new VictimGuidance.Match("4", 8, first.requestQuote(), 1000, first.purpose(), first.recipient(),
                "REFUSED", "我暂时不转", false, "");
        service.recordVictimGuidance(7, 9, match);
        var saved = r9.getVictimGuidanceJson();
        assertEquals(3, VictimGuidance.read(saved).consecutive());
        assertTrue(service.fundsContext(7).guidance().paymentAllowed(service.fundsContext(7)));
        service.recordVictimGuidance(7, 9, match);
        assertEquals(saved, r9.getVictimGuidanceJson()); verify(session, times(1)).bumpVersion();
        r9.setPhase(RoundPhase.VICTIM_GENERATING);
        var beforePayment = service.fundsContext(7).summary();
        when(session.getFundsSummary()).thenReturn(beforePayment);
        var savedVictim = new java.util.concurrent.atomic.AtomicReference<SimulationMessage>();
        when(messages.findByRoundIdAndSpeakerRole(9, SpeakerRole.VICTIM)).thenAnswer(i -> Optional.ofNullable(savedVictim.get()));
        when(messages.saveAndFlush(any())).thenAnswer(i -> {
            SimulationMessage message = i.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 90L); savedVictim.set(message); return message;
        });
        var actions = List.of(
                new FundAction(FundFlowType.RESERVE_RELEASE, FundPurpose.RESERVE_RELEASE, 750, 9, SpeakerRole.VICTIM,
                        "已释放普通预留750元", 1, "为本笔付款补足差额", null, FundReserveTier.GENERAL),
                new FundAction(FundFlowType.PAYMENT, FundPurpose.INVESTMENT_PAYMENT, 1000, 9, SpeakerRole.VICTIM,
                        "已实际转账1000元", 2, null, null, null));
        assertEquals(90L, service.saveMessage(7, 9, SpeakerRole.VICTIM, "好，这笔我照你说的办", "已释放普通预留750元，已实际转账1000元给模拟商户", "接受安排", null, actions));
        assertTrue(VictimGuidance.read(r9.getVictimGuidanceJson()).fulfilled());
        assertEquals(1000, r9.getRoundPaidYuan());
        verify(ledger, times(2)).save(any(FundFlow.class));
        assertNull(service.saveMessage(7, 9, SpeakerRole.VICTIM, "好", "已实际转账1000元", "接受", null, actions));
        verify(ledger, times(2)).save(any(FundFlow.class));
        assertTrue(service.victimAcceptedFacts(7, 10).contains("已实际支付，不得重复付款"));
        assertFalse(service.fundsContext(7).guidance().paymentAllowed(service.fundsContext(7)));
    }
}
