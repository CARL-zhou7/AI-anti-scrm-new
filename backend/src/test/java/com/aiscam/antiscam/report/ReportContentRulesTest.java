package com.aiscam.antiscam.report;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.report.service.ReportViewMapper;
import com.aiscam.antiscam.simulation.domain.SimulationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportContentRulesTest {
    private final ReportFundsSummary funds = new ReportFundsSummary(100,50,50);
    private final Map<Integer,List<String>> quotes = Map.of(1,List.of("我已经转账100元"));
    private ReportContentV2 content(int length, int nodes, int advice, ReportFundsSummary amount) {
        return new ReportContentV2(2,new ReportResult("已受骗：发生转账","实际转账后停止"),"文".repeat(length),
                java.util.Collections.nCopies(nodes,new ReportKeyNode(1,"转账100元","先付款后停止")),
                java.util.Collections.nCopies(advice,"通过原有号码独立核实"),amount);
    }
    @Test void boundsReferencesAndMoneyAreValidated() {
        for (int length : new int[]{1,80,120,299,300,400,800}) assertDoesNotThrow(() -> AgentOutputValidator.reportV2(content(length,1,2,funds),"已受骗",funds,quotes));
        for (int length : new int[]{0,801}) assertThrows(ReportValidationException.class,
                () -> AgentOutputValidator.reportV2(content(length,1,2,funds),"已受骗",funds,quotes));
        for (var value : List.of(content(400,4,2,funds),content(400,1,1,funds),content(400,1,4,funds),
                content(400,1,2,new ReportFundsSummary(100,0,100)))) assertThrows(ReportValidationException.class,
                () -> AgentOutputValidator.reportV2(value,"已受骗",funds,quotes));
        assertThrows(ReportValidationException.class, () -> AgentOutputValidator.reportV2(content(400,1,2,funds),"已受骗",funds,Map.of()));
        assertThrows(ReportValidationException.class, () -> AgentOutputValidator.reportV2(content(400,1,2,funds),"已识破",funds,quotes));
    }
    @Test void versionAndStrictJsonSurviveSerialization() throws Exception {
        var mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(content(400,1,2,funds));
        assertEquals(content(400,1,2,funds),AgentOutputParser.reportV2(json));
        assertThrows(ReportValidationException.class, () -> AgentOutputParser.reportV2(json.replace("\"totalPaidYuan\":100","\"totalPaidYuan\":100.5")));
        assertThrows(ReportValidationException.class, () -> AgentOutputParser.reportV2(json + " {}"));
        assertEquals(2,SimulationReport.pending(1,Instant.EPOCH,2).getContentSchemaVersion());
        assertEquals(1,SimulationReport.pending(1,Instant.EPOCH).getContentSchemaVersion());
        assertEquals(2,ReportViewMapper.contentV2(json,"已受骗",funds,quotes).contentSchemaVersion());
        for (String bad : List.of("{}",json.replace("\"result\":{", "\"result\":null,\"extra\":{") ,
                json.replace("\"contentSchemaVersion\":2", "\"contentSchemaVersion\":1"),
                json.replace("\"totalPaidYuan\":100", "\"totalPaidYuan\":100,\"totalPaidYuan\":100")))
            assertThrows(ReportValidationException.class, () -> AgentOutputParser.reportV2(bad));
        var old = new ReportContent("旧总结",List.of(new RoundAnalysis(1,"原分析")),"原话术","原行为","未受骗结束：原结局");
        String oldJson = mapper.writeValueAsString(old);
        assertEquals(1,mapper.readTree(oldJson).get("contentSchemaVersion").asInt());
        assertEquals(old,ReportViewMapper.content(oldJson,1,"未受骗结束"));
        assertThrows(ReportValidationException.class, () -> AgentOutputParser.reportV2(oldJson));
    }
}
