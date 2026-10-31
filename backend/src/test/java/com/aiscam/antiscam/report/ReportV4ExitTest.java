package com.aiscam.antiscam.report;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.report.service.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportV4ExitTest {
    private static final ReportFundsSummaryV3 FUNDS = new ReportFundsSummaryV3(0, 0, 0, 0, 0, 0, 0);
    private static ReportContentV3 content(String text) {
        return new ReportContentV3(3, new ReportResult(text, "用户主动离开演绎页面"), "过程记录。".repeat(70),
                List.of(), List.of("通过独立渠道核实身份", "付款前核实资金用途"), FUNDS);
    }

    @Test void equivalentExitWordingIsAcceptedWithoutAllowingAnInventedWinner() throws Exception {
        for (String text : List.of("未形成胜负结论", "未形成受骗或识破结论")) {
            var report = content(text);
            assertSame(report, AgentOutputValidator.reportV4(report, null, FUNDS, Map.of()));
            var view = ReportViewMapper.contentV4(new ObjectMapper().writeValueAsString(report), null, FUNDS, Map.of(), null, null);
            assertEquals(text, view.result().text());
            assertNull(view.winning());
            assertNull(view.finalTrustScore());
            for (String winner : List.of("骗子胜", "受害者胜")) {
                assertThrows(ReportValidationException.class,
                        () -> AgentOutputValidator.reportV4(content(text + "；" + winner), null, FUNDS, Map.of()));
            }
        }
        assertThrows(ReportValidationException.class,
                () -> AgentOutputValidator.reportV4(content("用户离开"), null, FUNDS, Map.of()));
        assertThrows(ReportValidationException.class,
                () -> AgentOutputValidator.reportV4(content("未形成胜负结论"), "骗子胜", FUNDS, Map.of()));
    }

    @Test void frozenLegacyTemplateGetsConsistentV4ExitContextOnRetry() throws Exception {
        var sessions = mock(SimulationSessionRepository.class);
        var rounds = mock(SimulationRoundRepository.class);
        var messages = mock(SimulationMessageRepository.class);
        var prompts = mock(AgentPromptRepository.class);
        var reports = mock(SimulationReportRepository.class);
        var flows = mock(FundFlowRepository.class);
        var snapshots = mock(ConfigurationSnapshots.class);
        var json = new ObjectMapper();
        when(snapshots.read(anyString())).thenAnswer(call -> json.readTree(call.<String>getArgument(0)));
        var session = mock(SimulationSession.class);
        when(session.getStatus()).thenReturn(SimulationStatus.INTERRUPTED);
        when(session.getInterruptionCode()).thenReturn(InterruptionCode.USER_EXIT);
        when(session.getReportPromptId()).thenReturn(14L);
        when(session.getScenarioSnapshot()).thenReturn("{}");
        when(session.getScammerSnapshot()).thenReturn("{}");
        when(session.getRuntimeSnapshot()).thenReturn("{\"ruleset_version\":\"PRD-V3.5\",\"report_content_schema_version\":4}");
        when(sessions.findById(115L)).thenReturn(Optional.of(session));
        var round = mock(SimulationRound.class);
        when(round.getPhase()).thenReturn(RoundPhase.COMPLETE);
        when(round.getRoundNo()).thenReturn(7);
        when(rounds.findBySessionIdOrderByRoundNoAsc(115L)).thenReturn(List.of(round));
        var prompt = mock(AgentPrompt.class);
        try (var in = getClass().getResourceAsStream("/prompts/v6/report.txt")) {
            when(prompt.getTemplateContent()).thenReturn(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        when(prompts.findById(14L)).thenReturn(Optional.of(prompt));
        when(reports.findById(115L)).thenReturn(Optional.of(SimulationReport.pending(115, Instant.EPOCH, 4)));
        var context = new ReportContextLoader(sessions, rounds, messages, prompts, snapshots, reports, flows).load(115);
        assertNull(context.outcomeDisplayText());
        assertTrue(context.variables().get("outcome").contains("未形成胜负结论"));
        assertFalse(context.variables().get("outcome").contains("未形成受骗或识破结论"));
        assertTrue(context.template().contains("优先于旧模板"));
        var expected = content("未形成胜负结论");
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains(context.variables().get("outcome")));
            try { return json.writeValueAsString(expected); } catch (Exception e) { throw new AssertionError(e); }
        }, new com.aiscam.antiscam.config.SimulationProperties.Llm());
        assertEquals(expected, runner.report(context, 1000));
    }
}
