package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreeSimulationTest {
    @Test void virtualBalanceIsNotARealOpeningFee() {
        assertTrue(AgentOutputValidator.freeSimulation("安排300元模拟盘体验，全程不扣费。", null));
        assertTrue(AgentOutputValidator.freeSimulation("提供100000元模拟资金，免费体验，不需要付款。", null));
        assertTrue(AgentOutputValidator.freeSimulation("使用100000元模拟资金练习。", null));
        assertFalse(AgentOutputValidator.freeSimulation("需支付300元才能开通免费模拟盘。", null));
        assertFalse(AgentOutputValidator.freeSimulation("需要支付300元开通模拟盘，后续交易免费。", null));
        assertFalse(AgentOutputValidator.freeSimulation("免费查看走势，先充值300元到实盘账户。", null));
    }

    @Test void freeExperienceRejectsRequestForRealRecipientAndAcceptsVirtualPractice() throws Exception {
        String template;
        try (var in = getClass().getResourceAsStream("/prompts/v17/victim.txt")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                "",template,"","公开背景",true,"",true);
        var output = new AtomicReference<>("300元模拟盘体验可以，我这就转过去。您把收款方式发我一下。");
        var runner = new AgentRunner(request -> "{\"line\":\"" + output.get()
                + "\",\"action\":null,\"psychology\":\"愿意体验\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"fundActions\":[]}",
                new SimulationProperties().getLlm());
        var current = new DialogueLine(4,SpeakerRole.SCAMMER,"安排300元模拟盘体验，全程不扣费。",null,null);
        var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000,0,0,0,0,List.of());
        assertThrows(AgentCallException.class, () -> runner.victim(scene,List.of(),current,4,60000,false,0,funds));
        output.set("是用模拟资金练习吧？我先体验一下，具体怎么操作？");
        assertTrue(runner.victim(scene,List.of(),current,4,60000,false,1,funds).fundActions().isEmpty());
    }
}
