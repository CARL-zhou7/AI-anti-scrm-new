package com.aiscam.antiscam.report;

import com.aiscam.antiscam.agent.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.report.service.ReportContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ReportQuoteBindingTest {
    private final ReportFundsSummaryV3 funds = new ReportFundsSummaryV3(100, 10, 50, 40, 960, 100, 50);

    private ReportContentV3 generate(int schema, String quote, Map<Integer, List<String>> sources) throws Exception {
        var content = new ReportContentV3(3, new ReportResult("骗子胜", "按保存结果复盘"), "受害者投入资金后结束往来。",
                List.of(new ReportKeyNode(3, quote, "受害者实际投入资金。", "首次投入资金")),
                List.of("核实平台资质", "不支付提现解冻费"), funds);
        String raw = new ObjectMapper().writeValueAsString(content);
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains("@SOURCE:3:1"));
            assertTrue(request.prompt().contains("不需引用时填null"));
            return raw;
        }, new SimulationProperties().getLlm());
        return (ReportContentV3) runner.report(new ReportContext("报告", Map.of(), "test", 15, "骗子胜",
                schema, null, sources, 2, funds), 10000);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void selectedActionIsBoundToSavedOriginalWithoutModelTranscription(int schema) throws Exception {
        String action = "已实际转账100元到投资平台账户";
        var report = generate(schema, "@SOURCE:3:2", Map.of(3, List.of("我先试这笔。", action)));
        assertEquals(action, report.keyNodes().getFirst().quote());
        assertEquals(funds, report.fundsSummary());
    }

    @ParameterizedTest
    @ValueSource(strings = {"@SOURCE:2:1", "@SOURCE:3:0", "@SOURCE:3:2", "@SOURCE:3:99999999999999999", "@SOURCE:wrong", "改写的原话"})
    void invalidSelectorsAndFabricatedQuotesStillFail(String quote) {
        assertThrows(ReportValidationException.class, () -> generate(4, quote, Map.of(3, List.of("原文"))));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void longSourcesAreTruncatedAtUnicodeBoundariesAndOptionalQuotesRemainOptional(int schema) throws Exception {
        String source = "已投入资金" + "😀".repeat(150);
        String quote = generate(schema, "@SOURCE:3:1", Map.of(3, List.of(source))).keyNodes().getFirst().quote();
        assertEquals(120, quote.codePointCount(0, quote.length()));
        assertTrue(source.startsWith(quote));
        assertNull(generate(schema, null, Map.of(3, List.of(source))).keyNodes().getFirst().quote());
        assertEquals("原文", generate(schema, "原文", Map.of(3, List.of("已有原文"))).keyNodes().getFirst().quote());
    }
}
