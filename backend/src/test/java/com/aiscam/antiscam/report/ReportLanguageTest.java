package com.aiscam.antiscam.report;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.report.service.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportLanguageTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void existingV2ReportUsesNaturalChineseWithoutRewritingNumbersOrQuotes() throws Exception {
        var funds=new ReportFundsSummary(0,0,0);
        String rawQuote="totalPaidYuan=0";
        var saved=new ReportContentV2(2,new ReportResult("已识破（IDENTIFIED）：拒绝转账", "第19轮停止配合为true，totalPaidYuan=0、actualLossYuan=0"),
                "过程事实。".repeat(65)+"\n\n最终为已识破（IDENTIFIED），权威资金摘要显示totalPaidYuan=0、totalRefundedYuan=0、actualLossYuan=0。",
                List.of(new ReportKeyNode(19,rawQuote,"cooperationStopped=true，goalAchieved=false")),
                List.of("核实后再付款","actualFraudOccurred=false，仍应独立核实"),funds);
        String stored=json.writeValueAsString(saved);
        var view=ReportViewMapper.contentV2(stored,"已识破",funds,Map.of(19,List.of(rawQuote)));
        assertEquals("已识破：拒绝转账",view.result().text());
        assertEquals("第19轮已停止配合，没有实际付款、没有实际资金损失",view.result().reason());
        assertEquals("已停止配合，尚未达到诈骗目标",view.keyNodes().getFirst().analysis());
        assertFalse(view.summary().contains("权威资金摘要"));assertFalse(view.summary().contains("totalPaidYuan"));
        assertEquals(rawQuote,view.keyNodes().getFirst().quote());assertEquals(funds,view.fundsSummary());
        assertEquals(saved,AgentOutputParser.reportV2(stored));
        assertEquals(2,view.summary().split("\\n\\s*\\n").length);
    }

    @Test void positiveAmountsAndHistoricalOutcomeNamesRemainAccurate() throws Exception {
        var saved=new ReportContent("totalPaidYuan=300元，totalRefundedYuan=50，actualLossYuan=250",List.of(new RoundAnalysis(1,"停止配合为false")),
                "核实身份","goalAchieved=false","未受骗结束（NOT_DEFRAUDED）：停止联系");
        var view=ReportViewMapper.content(json.writeValueAsString(saved),1,"未受骗结束");
        assertEquals("累计支付300元，实际收到返款50元，实际亏损250元",view.summary());
        assertEquals("尚未明确停止配合",view.roundAnalyses().getFirst().analysis());
        assertEquals("未受骗结束：停止联系",view.outcomeAnalysis());
        assertFalse(view.outcomeAnalysis().contains("已识破"));
    }

    @Test void reportInputDescribesAuthoritativeFactsInChineseAndScriptedReportsStillWork() throws Exception {
        var session=mock(SimulationSession.class);
        when(session.getScenarioSnapshot()).thenReturn("{}");when(session.getScammerSnapshot()).thenReturn("{}");when(session.getVictimSnapshot()).thenReturn("{}");
        when(session.getOutcomeCode()).thenReturn(OutcomeCode.IDENTIFIED);when(session.getOutcomeReason()).thenReturn("核实后拒绝继续联系");
        var round=new SimulationRound(1,19,Instant.EPOCH);ReflectionTestUtils.setField(round,"id",1L);
        round.saveAnalysis("核实","话术","手段","心理",0,"已核实",false,false,true,"[]",OutcomeCode.IDENTIFIED,"已识破",Instant.EPOCH);
        var snapshots=mock(ConfigurationSnapshots.class);
        when(snapshots.read("{}")).thenReturn(json.readTree("{\"name\":\"场景\",\"description\":\"简介\"}"));
        when(snapshots.scammerText("{}")).thenReturn("骗子");when(snapshots.victimText("{}")).thenReturn("受害者");
        var variables=ReportContextAssembler.variablesV2(session,List.of(round),Map.of(),snapshots,List.of());
        assertEquals("已识破：核实后拒绝继续联系",variables.get("outcome"));
        assertTrue(variables.get("funds").contains("累计支付0元"));
        assertFalse(variables.get("funds").contains("totalPaidYuan="));
        assertTrue(variables.get("key_facts").contains("已停止配合"));
        assertFalse(variables.get("key_facts").matches("(?s).*(true|false).*"));
        String template;
        try(var in=getClass().getResourceAsStream("/prompts/v2/report.txt")){template=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        String output=new ScriptedLlmClient(json).completeJson(new LlmClient.LlmRequest(AgentRole.REPORT,
                PromptRenderer.render(template,variables),0.3,4096,Duration.ofSeconds(2),0,0,"default"));
        var report=AgentOutputParser.reportV2(output);
        assertTrue(report.result().text().contains("已识破"));assertEquals(new ReportFundsSummary(0,0,0),report.fundsSummary());
    }
}
