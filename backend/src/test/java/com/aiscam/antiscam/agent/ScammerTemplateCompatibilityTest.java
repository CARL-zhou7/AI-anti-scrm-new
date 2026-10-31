package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.AgentScene;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.FundsContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ScammerTemplateCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"v16", "v18", "v19"})
    void firstRoundRendersPinnedTemplatesBeforeCallingTheModel(String version) throws Exception {
        String template;
        try (var in = getClass().getResourceAsStream("/prompts/" + version + "/scammer.txt")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean modern = !version.equals("v16");
        var scene = new AgentScene(ScenarioCode.TASK_REBATE,"刷单","简介","联系人","本人","test",
                template,"","","公开背景",true,null,true,false,modern,"不虚构到账");
        var runner = new AgentRunner(request -> {
            assertFalse(request.prompt().contains("{{"));
            assertTrue(request.prompt().contains(modern ? "固定15轮" : "剩余有效演绎时长约10秒"));
            return "{\"line\":\"你好，先给你介绍一下\",\"action\":null,\"intent\":\"介绍\","
                    + "\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"claimedRefundYuan\":null,\"fundActions\":[]}";
        },new SimulationProperties().getLlm());
        assertNotNull(runner.scammer(scene,List.of(),1,10000,0,
                new FundsContext(ScenarioCode.TASK_REBATE,3000,9000,0,0,0,List.of())));
    }
}
