package com.aiscam.antiscam.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.page.ContentCursor;
import com.aiscam.antiscam.history.page.HistoryCursor;
import com.aiscam.antiscam.history.service.*;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class HistoryRulesTest {
    private static final Instant T = Instant.parse("2026-10-19T02:00:00.123Z");
    private final SimulationRoundRepository rounds = mock(SimulationRoundRepository.class);
    private final SimulationMessageRepository messages = mock(SimulationMessageRepository.class);
    private final SimulationReportRepository reports = mock(SimulationReportRepository.class);
    private final ConfigurationSnapshots snapshots = new ConfigurationSnapshots(new ObjectMapper());
    private final SimulationStateAssembler state = new SimulationStateAssembler(rounds, messages, reports, snapshots, mock(TempAudioStore.class), mock(SimulationTrustEventRepository.class));
    private final FundFlowRepository funds = mock(FundFlowRepository.class);
    private final HistoryAssembler assembler = new HistoryAssembler(snapshots, state, rounds, messages, reports, funds);

    @Test
    void cursorPreservesMillisecondsAndRejectsTamperingOtherAccountsAndOtherUses() {
        var cursor = new HistoryCursor();
        String token = cursor.encode("7", T, 42);
        assertEquals(new HistoryCursor.Keyset(T, 42), cursor.decode("7", token));
        assertNull(cursor.decode("7", null)); assertNull(cursor.decode("7", ""));
        assertEquals("INVALID_CURSOR", assertThrows(ApiException.class, () -> cursor.decode("8", token)).code);
        byte[] tampered = Base64.getUrlDecoder().decode(token); tampered[0] ^= 1;
        for (String bad : List.of(Base64.getUrlEncoder().withoutPadding().encodeToString(tampered),
                token.substring(0, token.length() - 8), "not!a!cursor", "x".repeat(300),
                new ContentCursor().encode(ContentCursor.SCOPE_SCAMMERS, "7", 42))) {
            assertEquals("INVALID_CURSOR", assertThrows(ApiException.class, () -> cursor.decode("7", bad)).code);
        }
    }

    @Test
    void detailKeepsHalfRoundWithoutInventingAnalysisAndOmitsPrivateAndAudioFields() throws Exception {
        var s = session(SimulationStatus.INTERRUPTED);
        var round = new SimulationRound(1, 1, T);
        ReflectionTestUtils.setField(round, "id", 10L);
        var msg = new SimulationMessage(10, SpeakerRole.SCAMMER, "当时台词", "发送链接", "INTERNAL-SECRET", T);
        ReflectionTestUtils.setField(msg, "id", 11L);
        when(rounds.findBySessionIdOrderByRoundNoAsc(1)).thenReturn(List.of(round));
        when(messages.findByRoundIdIn(List.of(10L))).thenReturn(List.of(msg));
        var detail = assembler.detail(s, false);
        assertFalse(detail.funds().fundsRecorded());
        assertNull(detail.funds().availableBalanceYuan());
        assertEquals("PRD-V3.2", detail.rulesetVersion());
        var saved = detail.rounds().getFirst();
        assertNull(saved.funds());
        assertFalse(saved.interactionComplete()); assertFalse(saved.presentationComplete()); assertNull(saved.analysis());
        assertEquals("当时台词", saved.messages().getFirst().content());
        assertEquals("发送链接", saved.messages().getFirst().actionText());
        assertEquals("冻结场景", detail.configuration().scenario().name());
        assertEquals("当时骗子", detail.configuration().scammer().name());
        assertEquals("2026-10-19T02:00:00.123Z", detail.startedAt());
        assertNull(detail.report()); assertNull(detail.outcome()); assertEquals("USER_EXIT", detail.interruption().code());
        String json = new ObjectMapper().writeValueAsString(detail);
        for (String hidden : List.of("audioUrl", "audioDurationMs", "segments", "internalNote", "INTERNAL-SECRET", "userId")) assertFalse(json.contains(hidden));
        // The projection may ask whether a legacy report row exists, but it never reads or invents one.
        verify(reports, never()).findById(anyLong());
        verify(reports).existsById(anyLong());
    }

    @Test
    void finishedMissingReportDegradesOnlyInListAndSummariesKeepEveryReportState() {
        var s = session(SimulationStatus.FINISHED);
        assertNull(assembler.item(s, null).report());
        assertEquals("DATA_INCONSISTENT", assertThrows(ApiException.class, () -> assembler.detail(s, false)).code);
        for (ReportStatus status : ReportStatus.values()) {
            var row = SimulationReport.pending(1, T);
            ReflectionTestUtils.setField(row, "status", status);
            ReflectionTestUtils.setField(row, "errorCode", "TIMEOUT");
            var item = assembler.item(s, row);
            assertEquals(status.name(), item.report().status());
            assertEquals(status.displayText(), item.report().displayText());
            assertEquals(status == ReportStatus.FAILED, item.report().canRetry());
            assertEquals(status == ReportStatus.FAILED, item.report().error() != null);
            assertNotNull(item.outcome()); assertNull(item.interruption());
        }
        var running = assembler.item(session(SimulationStatus.RUNNING), SimulationReport.pending(1, T));
        assertNull(running.report()); assertNull(running.outcome()); assertNull(running.interruption());
    }

    @Test
    void listUsesTheWholeKeysetAndDetailRejectsForeignAndMissingRecordsIdentically() {
        var sessions = mock(SimulationSessionRepository.class);
        var cursors = new HistoryCursor();
        var service = new HistoryService(sessions, reports, cursors, assembler);
        var newer = session(SimulationStatus.INTERRUPTED);
        var older = session(SimulationStatus.INTERRUPTED);
        when(newer.getId()).thenReturn(2L);
        when(sessions.findByUserIdOrderByStartedAtDescIdDesc(7, PageRequest.of(0, 2))).thenReturn(List.of(newer, older));
        when(sessions.findPageBefore(7, T, 2, PageRequest.of(0, 2))).thenReturn(List.of(older));
        var first = service.list(7, "7", null, 1);
        assertTrue(first.hasMore()); assertEquals("2", first.items().getFirst().sessionId());
        var last = service.list(7, "7", first.nextCursor(), 1);
        assertFalse(last.hasMore()); assertNull(last.nextCursor()); assertEquals("1", last.items().getFirst().sessionId());
        when(sessions.findById(1L)).thenReturn(Optional.of(older));
        when(older.getUserId()).thenReturn(8L);
        var foreign = assertThrows(ApiException.class, () -> service.detail(7, "1", true));
        var missing = assertThrows(ApiException.class, () -> service.detail(7, "999", true));
        assertEquals(foreign.status, missing.status); assertEquals(foreign.code, missing.code);
        assertEquals(foreign.getMessage(), missing.getMessage());
        assertEquals(400, assertThrows(ApiException.class, () -> service.detail(7, "007", false)).status);
    }

    @Test
    void newHistoryAndLiveStateUseRecordedZeroAndRoundBalance() {
        var s = session(SimulationStatus.RUNNING);
        when(s.getSnapshotSchemaVersion()).thenReturn(2);
        when(s.getInitialAvailableFundsYuan()).thenReturn(10000);
        when(s.getFundsSummary()).thenReturn(new FundsSummary(10000, 10000, 0, 0, 0, 0, 0));
        var round = new SimulationRound(1, 1, T, s.getFundsSummary());
        ReflectionTestUtils.setField(round, "id", 10L);
        when(rounds.findBySessionIdOrderByRoundNoAsc(1)).thenReturn(List.of(round));
        var detail = assembler.detail(s, false);
        assertTrue(detail.funds().fundsRecorded());
        assertEquals(10000, detail.funds().availableBalanceYuan());
        assertEquals(0, detail.funds().totalPaidYuan());
        assertTrue(detail.funds().flows().isEmpty());
        assertEquals(10000, detail.rounds().getFirst().funds().availableBalanceYuan());
        assertEquals("PRD-V3.2", detail.rulesetVersion());
        assertEquals(10000, state.assemble(s).funds().availableBalanceYuan());
        assertNull(state.assemble(s).funds().flows());
    }

    @Test
    void ledgerProjectsOrderedFlowsAndPerRoundSummariesWithoutInternalRefundLinks() throws Exception {
        var session = session(SimulationStatus.INTERRUPTED);
        when(session.getSnapshotSchemaVersion()).thenReturn(2);
        when(session.getRuntimeSnapshot()).thenReturn("{\"ruleset_version\":\"PRD-V3.3\"}");
        var firstSummary = new FundsSummary(1000,900,100,0,100,0,0);
        var finalSummary = new FundsSummary(1000,850,300,50,250,1,100);
        when(session.getInitialAvailableFundsYuan()).thenReturn(1000);
        when(session.getFundsSummary()).thenReturn(finalSummary);
        var first = new SimulationRound(1,1,T,new FundsSummary(1000,1000,0,0,0,0,0));
        var second = new SimulationRound(1,2,T,firstSummary);
        ReflectionTestUtils.setField(first,"id",10L); ReflectionTestUtils.setField(second,"id",20L);
        var payment = new FundPosting(FundFlowType.PAYMENT,FundPurpose.INITIAL_PAYMENT,100,1,900,null,null);
        var additional = new FundPosting(FundFlowType.PAYMENT,FundPurpose.ADDITIONAL_PAYMENT,200,2,700,null,null);
        var refund = new FundPosting(FundFlowType.REFUND,FundPurpose.REFUND,50,3,750,null,100L);
        var reserve = new FundPosting(FundFlowType.RESERVE_RELEASE,FundPurpose.RESERVE_RELEASE,100,4,850,"调整安排",null);
        first.applyFunds(new FundApplication(List.of(payment),firstSummary));
        second.applyFunds(new FundApplication(List.of(additional,refund,reserve),finalSummary));
        when(rounds.findBySessionIdOrderByRoundNoAsc(1)).thenReturn(List.of(first,second));
        when(funds.findBySessionIdOrderBySequenceNoAsc(1)).thenReturn(List.of(new FundFlow(1,10,11L,payment,T),
                new FundFlow(1,20,21L,additional,T),new FundFlow(1,20,22L,refund,T),new FundFlow(1,20,23L,reserve,T)));
        var detail = assembler.detail(session, false);
        assertEquals("PRD-V3.3",detail.rulesetVersion());
        assertEquals(List.of(1,2,3,4),detail.funds().flows().stream().map(f -> f.sequenceNo()).toList());
        assertEquals(List.of(900,700,750,850),detail.funds().flows().stream().map(f -> f.balanceAfterYuan()).toList());
        assertEquals(List.of(1,2,2,2),detail.funds().flows().stream().map(f -> f.roundNo()).toList());
        assertEquals("22",detail.funds().flows().get(2).messageId());
        assertEquals(100,detail.rounds().getFirst().funds().totalPaidYuan());
        assertEquals(200,detail.rounds().get(1).funds().totalPaidYuan());
        assertEquals(50,detail.rounds().get(1).funds().totalRefundedYuan());
        assertEquals(100,detail.rounds().get(1).funds().reserveReleasedYuan());
        assertEquals(250,detail.rounds().get(1).funds().actualLossYuan());
        String json = new ObjectMapper().writeValueAsString(detail);
        for (String hidden : List.of("relatedPaymentId","related_payment_id","runtime_snapshot","internalNote","prompt")) assertFalse(json.contains(hidden));
    }

    @Test
    void v35HistoryIncludesRoundScoresOnlyWhenRequested() throws Exception {
        var s = session(SimulationStatus.FINISHED);
        when(s.getSnapshotSchemaVersion()).thenReturn(4);
        when(s.getRuntimeSnapshot()).thenReturn("{\"ruleset_version\":\"PRD-V3.5\",\"fund_ruleset_version\":\"funds-v3\"}");
        when(s.getInitialTrustScore()).thenReturn(50);
        when(s.getWinner()).thenReturn("SCAMMER");
        when(s.getFinalTrustScore()).thenReturn(62);
        when(s.getOutcomeCode()).thenReturn(OutcomeCode.SCAMMER_WIN);
        when(s.getOutcomeReason()).thenReturn("第15轮结束时最终受骗倾向分62且无实际损失");
        when(s.getInterruptionCode()).thenReturn(null);
        when(s.getInterruptionDetail()).thenReturn(null);
        when(s.getCurrentRoundNo()).thenReturn(1);
        when(s.getActualLossYuan()).thenReturn(0);
        when(s.getInitialAvailableFundsYuan()).thenReturn(10000);
        when(s.getFundsSummary()).thenReturn(new FundsSummary(10000, 10000, 0, 0, 0, 0, 0));

        var round = mock(SimulationRound.class);
        when(round.getId()).thenReturn(10L);
        when(round.getRoundNo()).thenReturn(1);
        when(round.getPhase()).thenReturn(RoundPhase.COMPLETE);
        when(round.hasAnalysis()).thenReturn(true);
        when(round.getFraudStage()).thenReturn("取得信任");
        when(round.getSpeechAnalysis()).thenReturn("完整话术分析：先建立身份再提出转账");
        when(round.getMethodAnalysis()).thenReturn("完整手段分析：冒充亲友借款");
        when(round.getMethodShort()).thenReturn("冒充亲友");
        when(round.getIntentShort()).thenReturn("借亲属身份让受害人放下戒心后转账");
        when(round.getVictimPsychology()).thenReturn("半信半疑");
        when(round.getRiskScore()).thenReturn(60);
        when(round.getRiskReason()).thenReturn("涉及转账");
        when(rounds.findBySessionIdOrderByRoundNoAsc(1)).thenReturn(List.of(round));
        when(rounds.findBySessionIdAndRoundNo(1, 1)).thenReturn(Optional.of(round));
        when(rounds.countBySessionIdAndPhase(1L, RoundPhase.COMPLETE)).thenReturn(1L);
        var scammer = new SimulationMessage(10, SpeakerRole.SCAMMER, "台词", null, null, T);
        var victim = new SimulationMessage(10, SpeakerRole.VICTIM, "回应", null, null, T);
        ReflectionTestUtils.setField(scammer, "id", 11L);
        ReflectionTestUtils.setField(victim, "id", 12L);
        when(messages.findByRoundIdIn(List.of(10L))).thenReturn(List.of(scammer, victim));
        when(reports.findById(1L)).thenReturn(Optional.of(SimulationReport.pending(1, T)));
        when(funds.findBySessionIdOrderBySequenceNoAsc(1)).thenReturn(List.of());
        var trustEvents = mock(SimulationTrustEventRepository.class);
        var scored = mock(SimulationTrustEvent.class);
        when(scored.getRoundNo()).thenReturn(1);
        when(scored.getScoreAfter()).thenReturn(62);
        when(trustEvents.findBySessionIdOrderByIdAsc(1L)).thenReturn(List.of(scored));
        var live = new SimulationStateAssembler(rounds, messages, reports, snapshots, mock(TempAudioStore.class),
                trustEvents);
        var history = new HistoryAssembler(snapshots, live, rounds, messages, reports, funds);

        var detail = history.detail(s, false);

        var analysis = detail.rounds().getFirst().analysis();
        assertNull(analysis.trustChange(), "V3.5 history omits the per-round score by default");
        assertEquals("完整话术分析：先建立身份再提出转账", analysis.speechAnalysis());
        assertEquals("完整手段分析：冒充亲友借款", analysis.methodAnalysis());
        assertEquals("冒充亲友", analysis.methodShort(), "history keeps both fields, it does not substitute shorts");
        assertEquals("SCAMMER", detail.outcome().winning());
        assertEquals(62, detail.outcome().finalTrustScore());
        var requested = history.detail(s, true);
        var change = new com.aiscam.antiscam.simulation.web.dto.SimulationViews.TrustChange(50, 62, 12);
        assertEquals(change, live.analysis(s, round).trustChange());
        assertEquals(change, requested.rounds().getFirst().analysis().trustChange());
        String json = new ObjectMapper().writeValueAsString(requested);
        for (String hidden : List.of("counterevidence", "trust_events", "threshold", "positiveFactor",
                "negativeFactor", "coefficient")) assertFalse(json.contains(hidden));

        // An old PRD-V3.4 run keeps whatever it recorded; the projection is not rewritten for legacy history.
        when(s.getRuntimeSnapshot()).thenReturn("{\"ruleset_version\":\"PRD-V3.4\",\"fund_ruleset_version\":\"funds-v3\"}");
        var legacy = history.detail(s, false);
        assertEquals(new com.aiscam.antiscam.simulation.web.dto.SimulationViews.TrustChange(50, 62, 12),
                legacy.rounds().getFirst().analysis().trustChange());
    }

    private SimulationSession session(SimulationStatus status) {
        var s = mock(SimulationSession.class);
        when(s.getId()).thenReturn(1L); when(s.getStatus()).thenReturn(status); when(s.getStartedAt()).thenReturn(T);
        when(s.getDeadlineAt()).thenReturn(T.plusSeconds(600));
        when(s.getRuntimeSnapshot()).thenReturn("{\"ruleset_version\":\"PRD-V3.2\"}");
        when(s.getOutcomeCode()).thenReturn(OutcomeCode.NOT_DEFRAUDED);
        when(s.getOutcomeReason()).thenReturn("核实后停止联系");
        when(s.getInterruptionCode()).thenReturn(InterruptionCode.USER_EXIT);
        when(s.getInterruptionDetail()).thenReturn("用户退出");
        when(s.getScenarioSnapshot()).thenReturn("{\"id\":\"1\",\"scenario_code\":\"TASK_REBATE\",\"name\":\"冻结场景\"}");
        String common = "\"id\":\"1\",\"avatar_key\":\"avatars/" + "a".repeat(64) + ".png\","
                + "\"speaking_avatar_key\":\"avatars/" + "b".repeat(64) + ".png\",";
        when(s.getScammerSnapshot()).thenReturn("{" + common + "\"name\":\"当时骗子\",\"fraud_experience\":\"HIGH\",\"speech_skill\":\"MEDIUM\",\"manipulation_skill\":\"LOW\"}");
        when(s.getVictimSnapshot()).thenReturn("{" + common + "\"name\":\"当时受害者\",\"anti_fraud_awareness\":\"LOW\",\"risk_tendency\":\"HIGH\"}");
        return s;
    }
}
