package com.aiscam.antiscam.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.report.service.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportRulesTest {
    private static final Instant T = Instant.parse("2026-10-19T02:00:00Z");
    private static final String JSON = """
            {"summary":"过程总结","roundAnalyses":[{"roundNo":1,"analysis":"独立核实"}],
             "speechAnalysis":"制造紧迫感","victimBehaviorAnalysis":"核实后停止配合","outcomeAnalysis":"未受骗结束：未执行危险操作"}
            """;

    @Test
    void parserAndValidatorRejectMalformedOrInconsistentReports() {
        var valid = AgentOutputParser.report(JSON);
        assertSame(valid, AgentOutputValidator.report(valid, 1, "未受骗结束"));
        for (String bad : List.of(JSON.replace("\"summary\":", "\"extra\":0,\"summary\":"),
                JSON.replace("\"roundNo\":1", "\"roundNo\":\"1\""), JSON + " {}",
                JSON.replace("\"summary\":", "\"summary\":\"重复\",\"summary\":"))) {
            assertThrows(ReportValidationException.class, () -> AgentOutputParser.report(bad));
        }
        for (String bad : List.of(JSON.replace("过程总结", " "), JSON.replace("独立核实", "<b>核实</b>"),
                JSON.replace("过程总结", "😀".repeat(601)), JSON.replace("\"roundNo\":1", "\"roundNo\":2"))) {
            assertThrows(ReportValidationException.class, () -> AgentOutputValidator.report(AgentOutputParser.report(bad), 1, "未受骗结束"));
        }
        assertThrows(ReportValidationException.class, () -> AgentOutputValidator.report(valid, 2, "未受骗结束"));
        assertThrows(ReportValidationException.class, () -> AgentOutputValidator.report(valid, 1, "结局未定"));
    }

    @Test
    void retryOnlyRequeuesFailedFinishedReports() {
        for (var status : SimulationStatus.values()) {
            for (var report : ReportStatus.values()) {
                var expected = status != SimulationStatus.FINISHED ? ReportRetryRules.Action.NOT_APPLICABLE
                        : report == ReportStatus.FAILED ? ReportRetryRules.Action.REQUEUE : ReportRetryRules.Action.RETURN_EXISTING;
                assertEquals(expected, ReportRetryRules.decide(status, report));
                assertEquals(report == ReportStatus.FAILED, report.canRetry());
            }
        }
        assertEquals(ReportRetryRules.Action.INCONSISTENT, ReportRetryRules.decide(SimulationStatus.FINISHED, null));
        var row = SimulationReport.pending(1, T);
        row.claim("token", T, T.plusSeconds(120));
        assertEquals(1, row.getAttemptNo());
        assertThrows(IllegalStateException.class, () -> row.claim("other", T, T.plusSeconds(120)));
        ReflectionTestUtils.setField(row, "status", ReportStatus.FAILED);
        ReflectionTestUtils.setField(row, "errorCode", "TIMEOUT");
        row.requeue(T.plusSeconds(130));
        assertEquals(1, row.getAttemptNo());
        assertEquals(T.plusSeconds(130), row.getQueuedAt());
        assertNull(row.getTaskToken()); assertNull(row.getStartedAt()); assertNull(row.getAttemptDeadlineAt());
        assertNull(row.getFinishedAt()); assertNull(row.getErrorCode()); assertNull(row.getContentJson());
    }

    @Test
    void contextUsesOnlySavedSnapshotsAndContainsExactPromptVariables() throws Exception {
        var session = mock(SimulationSession.class);
        when(session.getScenarioSnapshot()).thenReturn("{\"name\":\"冻结场景\",\"description\":\"当时简介\"}");
        when(session.getScammerSnapshot()).thenReturn("{\"name\":\"当时骗子\"}");
        when(session.getVictimSnapshot()).thenReturn("{\"name\":\"当时受害者\"}");
        when(session.getOutcomeCode()).thenReturn(OutcomeCode.UNDETERMINED);
        when(session.getOutcomeReason()).thenReturn("达到限制");
        when(session.getLimitReason()).thenReturn(LimitReason.ROUND_LIMIT);
        var round = new SimulationRound(1, 1, T);
        ReflectionTestUtils.setField(round, "id", 10L);
        var scammer = new SimulationMessage(10, SpeakerRole.SCAMMER, "快操作", "发送链接", "内部意图", T);
        var victim = new SimulationMessage(10, SpeakerRole.VICTIM, "我先核实", null, "内部心理", T);
        var vars = ReportContextAssembler.variables(session, List.of(round), Map.of(10L, List.of(victim, scammer)),
                new ConfigurationSnapshots(new ObjectMapper()));
        assertEquals(java.util.Set.of("scenario", "profiles", "outcome", "rounds"), vars.keySet());
        try (var in = getClass().getResourceAsStream("/prompts/v1/report.txt")) {
            assertNotNull(in);
            assertTrue(PromptRenderer.render(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), vars).contains("冻结场景"));
        }
        assertTrue(vars.get("rounds").contains("行动：发送链接；内部意图：内部意图"));
        assertTrue(vars.get("rounds").contains("本轮无解说"));
        assertTrue(vars.get("rounds").indexOf("骗子：") < vars.get("rounds").indexOf("受害者："));
        assertTrue(vars.get("outcome").contains("结局未定"));
        assertTrue(vars.get("profiles").contains("当时受害者"));
    }

    @Test
    void reportViewUsesSavedContentAndRiskPointsAndHidesNonSucceededContent() {
        var rounds = mock(SimulationRoundRepository.class);
        var reports = mock(SimulationReportRepository.class);
        var assembler = new SimulationStateAssembler(rounds, mock(SimulationMessageRepository.class), reports,
                mock(ConfigurationSnapshots.class), mock(TempAudioStore.class));
        var session = mock(SimulationSession.class);
        when(session.getId()).thenReturn(1L);
        when(session.getStatus()).thenReturn(SimulationStatus.FINISHED);
        when(session.getOutcomeCode()).thenReturn(OutcomeCode.NOT_DEFRAUDED);
        var row = SimulationReport.pending(1, T);
        when(reports.findById(1L)).thenReturn(Optional.of(row));
        var r1 = new SimulationRound(1, 1, T);
        r1.saveAnalysis("阶段", "话术", "手段", "心理", 25, "依据", false, false, false, "[]", null, null, T);
        var r2 = new SimulationRound(1, 2, T);
        var r3 = new SimulationRound(1, 3, T);
        r3.saveAnalysis("阶段", "话术", "手段", "心理", 75, "依据", false, false, false, "[]", null, null, T);
        when(rounds.findBySessionIdOrderByRoundNoAsc(1L)).thenReturn(List.of(r1, r2, r3));
        var view = assembler.reportView(session);
        assertEquals(List.of(1, 3), view.riskSeries().stream().map(p -> p.roundNo()).toList());
        assertEquals(List.of(25, 75), view.riskSeries().stream().map(p -> p.score()).toList());
        assertNull(view.content()); assertNull(view.report().error());
        ReflectionTestUtils.setField(row, "status", ReportStatus.FAILED);
        ReflectionTestUtils.setField(row, "errorCode", "TIMEOUT");
        assertEquals("报告生成超时，可手动重试。", assembler.reportView(session).report().error().message());
        assertNull(assembler.reportView(session).content());
        ReflectionTestUtils.setField(row, "status", ReportStatus.SUCCEEDED);
        ReflectionTestUtils.setField(row, "contentJson", JSON);
        when(rounds.findBySessionIdOrderByRoundNoAsc(1L)).thenReturn(List.of(r1));
        assertEquals("过程总结", assembler.reportView(session).content().summary());
        when(reports.findById(1L)).thenReturn(Optional.empty());
        assertEquals("DATA_INCONSISTENT", assertThrows(ApiException.class, () -> assembler.reportView(session)).code);
        when(session.getStatus()).thenReturn(SimulationStatus.INTERRUPTED);
        assertEquals("SIMULATION_INTERRUPTED", assembler.reportView(session).unavailableReason());
        assertTrue(assembler.reportView(session).riskSeries().isEmpty());
    }
}
