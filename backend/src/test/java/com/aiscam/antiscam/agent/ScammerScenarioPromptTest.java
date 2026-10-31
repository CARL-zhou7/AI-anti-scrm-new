package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.FundsContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ScammerScenarioPromptTest {
    @ParameterizedTest
    @EnumSource(ScenarioCode.class)
    void rendersTheSelectedScenarioWithoutTheSharedEightYuanExample(ScenarioCode scenario) throws Exception {
        String template;
        try (var in = getClass().getResourceAsStream("/prompts/v11/scammer.txt")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var scene = new AgentScene(scenario, scenario.name(), "场景说明", "角色背景", "本人背景", "test",
                template, "unused", "unused", "公开背景", true, "unused", true);
        var runner = new AgentRunner(request -> {
            String prompt = request.prompt();
            assertFalse(prompt.contains("{{"));
            assertFalse(prompt.contains("首单报酬8元"));
            assertFalse(prompt.contains("claimedRefundYuan=8"));
            assertFalse(prompt.contains("任务链接"));
            String direction = prompt.substring(prompt.indexOf("推进方向："), prompt.indexOf("【你的人物画像】"));
            switch (scenario) {
                case TASK_REBATE -> {
                    assertTrue(direction.contains("诱导对方垫付首笔小额资金"));
                    assertTrue(direction.contains("不套用投资认购或亲友借款流程"));
                }
                case FAKE_INVESTMENT -> {
                    assertTrue(direction.contains("始终围绕投资项目、出资、收益与提现展开"));
                    assertTrue(direction.contains("不转为刷单、接单、完成任务拿佣金或首单免垫付"));
                    assertTrue(direction.contains("未经出资不得虚构已产生投资收益"));
                }
                case IMPERSONATION_LOAN -> {
                    assertTrue(direction.contains("保持亲友借款语境"));
                    assertTrue(direction.contains("不通过小额返利建立信任"));
                    assertTrue(direction.contains("沿用首次明确的借款主事件"));
                    assertTrue(direction.contains("不从合同或医疗借款跳到网络借贷"));
                    assertTrue(prompt.contains("亲友借款的开局选择"));
                }
            }
            assertTrue(prompt.contains("仅承诺未来到账时"));
            assertTrue(prompt.contains("fundActions必须为空数组"));
            return "{\"line\":\"我先说明具体情况\",\"action\":null,\"intent\":\"建立联系\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"claimedRefundYuan\":null,\"fundActions\":[]}";
        }, new SimulationProperties().getLlm());
        var funds = new FundsContext(scenario, 3000, 9000, 0, 0, 0, List.of());
        assertNotNull(runner.scammer(scene, List.of(), 8, 10000, 0, funds));
        assertNotNull(new db.migration.V35__keep_loan_story_and_separate_risk().getChecksum());
    }
}
