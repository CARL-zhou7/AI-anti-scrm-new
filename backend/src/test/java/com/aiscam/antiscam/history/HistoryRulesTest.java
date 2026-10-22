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
    private final SimulationStateAssembler state = new SimulationStateAssembler(rounds, messages, reports, snapshots, mock(TempAudioStore.class));
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
        var detail = assembler.detail(s);
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
        verifyNoInteractions(reports);
    }

    @Test
    void finishedMissingReportDegradesOnlyInListAndSummariesKeepEveryReportState() {
        var s = session(SimulationStatus.FINISHED);
        assertNull(assembler.item(s, null).report());
        assertEquals("DATA_INCONSISTENT", assertThrows(ApiException.class, () -> assembler.detail(s)).code);
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
        var foreign = assertThrows(ApiException.class, () -> service.detail(7, "1"));
        var missing = assertThrows(ApiException.class, () -> service.detail(7, "999"));
        assertEquals(foreign.status, missing.status); assertEquals(foreign.code, missing.code);
        assertEquals(foreign.getMessage(), missing.getMessage());
        assertEquals(400, assertThrows(ApiException.class, () -> service.detail(7, "007")).status);
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
        var detail = assembler.detail(s);
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
        var detail = assembler.detail(session);
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
