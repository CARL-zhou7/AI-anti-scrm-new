package com.aiscam.antiscam.report;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.report.rules.ReportEligibility;
import com.aiscam.antiscam.report.service.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.aiscam.antiscam.sse.SimulationEventHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportEligibilityTest {
    @Test void generatedTrustOutcomeCanBeReadDuringReportPublication() throws Exception {
        for (var code : List.of(OutcomeCode.LIMIT_REACHED,OutcomeCode.DEFRAUDED)) {
            var s=session("PRD-V3.4",3);
            ReflectionTestUtils.setField(s,"runtimeSnapshot","{\"trust_config\":{\"trust_ruleset_version\":\"trust-v2\"}}");
            s.finish(code,"达到上限",LimitReason.ROUND_LIMIT,T);
            var funds=com.aiscam.antiscam.agent.AgentModels.ReportFundsSummaryV3.from(s);
            var content=new com.aiscam.antiscam.agent.AgentModels.ReportContentV3(3,
                    new com.aiscam.antiscam.agent.AgentModels.ReportResult(ReportContextAssembler.outcomeText(s,List.of()),"达到上限"),
                    "过程记录。".repeat(70),List.of(),List.of("独立核实身份","拒绝不明收费"),funds);
            var row=SimulationReport.pending(1,T,3);
            ReflectionTestUtils.setField(row,"status",ReportStatus.SUCCEEDED);
            ReflectionTestUtils.setField(row,"contentJson",new ObjectMapper().writeValueAsString(content));
            var rounds=mock(SimulationRoundRepository.class);
            var reports=mock(SimulationReportRepository.class);
            when(reports.findById(1L)).thenReturn(Optional.of(row));
            var assembler=new SimulationStateAssembler(rounds,mock(SimulationMessageRepository.class),reports,
                    new ConfigurationSnapshots(new ObjectMapper()),mock(TempAudioStore.class),mock(SimulationTrustEventRepository.class));
            assertNotNull(assembler.reportView(s).content());
        }
    }
    private static final Instant T=Instant.parse("2026-10-25T00:00:00Z");
    private SimulationSession session(String rules, int schema) {
        var s=new SimulationSession(1,"request",1,1,1,"{}","{}","{}",11,12,13,14,
                "{\"ruleset_version\":\""+rules+"\",\"report_content_schema_version\":"+schema+"}",T,T,"{}",1000);
        ReflectionTestUtils.setField(s,"id",1L); return s;
    }
    @Test void eligibilityUsesFrozenRulesAndCompleteRoundInsteadOfExitAlone() {
        for (var code : List.of(InterruptionCode.USER_EXIT,InterruptionCode.FUNDS_EXHAUSTED,InterruptionCode.CHARACTER_EXIT)) {
            var s=session("PRD-V3.4",3); s.interrupt(code,T);
            assertTrue(ReportEligibility.isEligible(s,1)); assertFalse(ReportEligibility.isEligible(s,0));
            var old=session("PRD-V3.3",2); old.interrupt(code,T);
            assertTrue(ReportEligibility.isLegacyExit(old)); assertFalse(ReportEligibility.isEligible(old,3));
        }
        var technical=session("PRD-V3.4",3); technical.interrupt(InterruptionCode.ANALYSIS_FAILED,T);
        assertFalse(ReportEligibility.isEligible(technical,1));
        for (String rules : List.of("PRD-V3.2","PRD-V3.3","PRD-V3.4")) {
            var finished=session(rules,1); finished.finish(OutcomeCode.NOT_DEFRAUDED,"正式结果",null,T);
            assertTrue(ReportEligibility.isEligible(finished,0));
        }
    }
    @Test void normalEndingHasReportAndRiskWithoutOutcomeButGetNeverRepairsMissingRow() {
        var rounds=mock(SimulationRoundRepository.class); var reports=mock(SimulationReportRepository.class);
        var assembler=new SimulationStateAssembler(rounds,mock(SimulationMessageRepository.class),reports,
                new ConfigurationSnapshots(new ObjectMapper()),mock(TempAudioStore.class), mock(SimulationTrustEventRepository.class));
        var s=session("PRD-V3.4",3); s.interrupt(InterruptionCode.USER_EXIT,T);
        ReflectionTestUtils.setField(s,"initialTrustScore",65);
        var round=new SimulationRound(1,1,T);
        round.saveAnalysis("阶段","话术","手段","心理",25,"依据",false,false,false,"[]",null,null,T);
        round.completePresentation(T,T.plusSeconds(2));
        when(rounds.countBySessionIdAndPhase(1,RoundPhase.COMPLETE)).thenReturn(1L);
        when(rounds.findBySessionIdOrderByRoundNoAsc(1)).thenReturn(List.of(round));
        when(reports.findById(1L)).thenReturn(Optional.of(SimulationReport.pending(1,T,3)));
        var view=assembler.reportView(s);
        assertNotNull(view.report()); assertNull(view.outcome()); assertNull(view.unavailableReason());
        assertEquals("USER_EXIT",view.ending().code()); assertTrue(view.ending().reason().contains("未形成受骗或识破结论"));
        assertEquals(25,view.riskSeries().getFirst().score());
        assertEquals(new com.aiscam.antiscam.simulation.web.dto.SimulationViews.TrustChange(65,65,0),view.riskSeries().getFirst().trustChange());
        when(reports.findById(1L)).thenReturn(Optional.empty());
        assertEquals("DATA_INCONSISTENT",assertThrows(ApiException.class,()->assembler.reportView(s)).code);
        verify(reports,never()).save(any());
        var old=session("PRD-V3.3",2); old.interrupt(InterruptionCode.USER_EXIT,T);
        assertEquals("LEGACY_NO_REPORT",assembler.reportView(old).unavailableReason());
        when(rounds.countBySessionIdAndPhase(1,RoundPhase.COMPLETE)).thenReturn(0L);
        assertEquals("NO_DIALOGUE",assembler.reportView(s).unavailableReason());
    }
    @Test void recoverySkipsLegacyExitAndOldFailedReportsKeepPinnedSchemaAndPrompt() {
        var reports=mock(SimulationReportRepository.class); var sessions=mock(SimulationSessionRepository.class);
        var rounds=mock(SimulationRoundRepository.class);
        var assembler=mock(SimulationStateAssembler.class);
        var transitions=new ReportTransitions(reports,sessions,assembler,mock(SimulationEventHub.class),new SimulationProperties(),
                new ConfigurationSnapshots(new ObjectMapper()),rounds);
        var oldExit=session("PRD-V3.3",2); oldExit.interrupt(InterruptionCode.USER_EXIT,T);
        when(sessions.lockById(1)).thenReturn(Optional.of(oldExit));
        assertFalse(transitions.repairMissing(1,T)); verify(reports,never()).saveAndFlush(any());
        assertEquals("REPORT_NOT_APPLICABLE",assertThrows(ApiException.class,()->transitions.retry(oldExit,T)).code);
        for (int schema : new int[]{1,2}) {
            var old=session(schema==1?"PRD-V3.2":"PRD-V3.3",schema); old.finish(OutcomeCode.NOT_DEFRAUDED,"已核实",null,T);
            var row=SimulationReport.pending(1,T,schema); ReflectionTestUtils.setField(row,"status",ReportStatus.FAILED);
            when(reports.lockById(1)).thenReturn(Optional.of(row));
            assertEquals(202,transitions.retry(old,T).httpStatus());
            assertEquals(schema,row.getContentSchemaVersion()); assertEquals(14,old.getReportPromptId());
            assertEquals(ReportStatus.PENDING,row.getStatus());
        }
    }
}
