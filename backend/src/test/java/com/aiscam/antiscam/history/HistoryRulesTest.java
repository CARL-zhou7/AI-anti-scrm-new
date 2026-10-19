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
    private final HistoryAssembler assembler = new HistoryAssembler(snapshots, state, rounds, messages, reports);

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
        var saved = detail.rounds().getFirst();
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

    private SimulationSession session(SimulationStatus status) {
        var s = mock(SimulationSession.class);
        when(s.getId()).thenReturn(1L); when(s.getStatus()).thenReturn(status); when(s.getStartedAt()).thenReturn(T);
        when(s.getDeadlineAt()).thenReturn(T.plusSeconds(600));
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
