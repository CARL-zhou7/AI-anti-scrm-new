package com.aiscam.antiscam.report;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.report.service.ReportViewMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportV3RulesTest {
    @Test void sevenFactsRoundTripAndNoOutcomeCannotInventConclusion() throws Exception {
        var funds=new ReportFundsSummaryV3(100,10,50,40,960,100,50);
        var c=new ReportContentV3(3,new ReportResult("未形成受骗或识破结论","过程正常结束"),"过程记录。".repeat(70),
                List.of(),List.of("核实身份","拒绝追加付款"),funds);
        String json=new ObjectMapper().writeValueAsString(c);
        assertEquals(c,AgentOutputValidator.reportV3(AgentOutputParser.reportV3(json),null,funds,Map.of()));
        assertEquals(3,ReportViewMapper.contentV3(json,null,funds,Map.of()).contentSchemaVersion());
        assertThrows(ReportValidationException.class,()->AgentOutputParser.reportV3(json.replace("\"totalRewardedYuan\":50","\"totalRewardedYuan\":50.5")));
        assertThrows(ReportValidationException.class,()->AgentOutputParser.reportV2(json));
        assertThrows(ReportValidationException.class,()->AgentOutputValidator.reportV3(AgentOutputParser.reportV3(json.replace("过程正常结束","已受骗")),null,funds,Map.of()));
        assertThrows(ReportValidationException.class,()->AgentOutputValidator.reportV3(c,null,new ReportFundsSummaryV3(100,10,50,50,960,100,50),Map.of()));
    }

    @Test void conciseSummaryAndEventTitleKeepLedgerAndQuoteValidation() throws Exception {
        var funds = new ReportFundsSummaryV3(100,10,50,40,960,100,50);
        var node = new ReportKeyNode(1,"先交钱","对方要求先付费，受害者实际付款。","对方要求先付费再办理");
        var content = new ReportContentV3(3,new ReportResult("骗子胜","程序保存结果"),
                "对方以兼职邀约接触受害者，之后要求垫资。受害者实际付款，收到部分返款和独立报酬后结束。",
                List.of(node),List.of("不为兼职垫资","通过独立渠道核实招募信息"),funds);
        String json = new ObjectMapper().writeValueAsString(content);
        var quotes = Map.of(1,List.of("请先交钱再办理"));
        String template;
        try (var in = getClass().getResourceAsStream("/prompts/v20/report.txt")) {
            template = new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains("80—120字"));
            assertFalse(request.prompt().contains("四段"));
            return json;
        },new com.aiscam.antiscam.config.SimulationProperties.Llm());
        var context = new com.aiscam.antiscam.report.service.ReportContext(template,
                Map.of("scenario","场景","profiles","公开画像","outcome","骗子胜","rounds","已保存对话",
                        "key_facts","已付款","funds","保存的七项资金"),"default",1,"骗子胜",4,null,quotes,1,funds);
        assertEquals(content,runner.report(context,10000));
        assertEquals(content,AgentOutputValidator.reportV4(AgentOutputParser.reportV3(json),"骗子胜",funds,quotes));
        assertEquals(node.title(),ReportViewMapper.contentV4(json,"骗子胜",funds,quotes,"SCAMMER",80).keyNodes().getFirst().title());
        assertThrows(ReportValidationException.class,()->AgentOutputValidator.reportV4(content,"骗子胜",funds,Map.of(1,List.of("无此原话"))));
        assertThrows(ReportValidationException.class,()->AgentOutputValidator.reportV4(content,"受害者胜",funds,quotes));
        // Old saved JSON has no title and remains readable with its original long summary.
        String legacy = json.replace(",\"title\":\"对方要求先付费再办理\"", "");
        assertNull(ReportViewMapper.contentV4(legacy,"骗子胜",funds,quotes,"SCAMMER",80).keyNodes().getFirst().title());
    }
}
