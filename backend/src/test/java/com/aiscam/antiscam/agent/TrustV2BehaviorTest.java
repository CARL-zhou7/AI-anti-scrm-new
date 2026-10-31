package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrustV2BehaviorTest {
    private FundsContext funds(int score, int paid) {
        return new FundsContext(ScenarioCode.IMPERSONATION_LOAN,10000,0,0,paid,0,List.of(),
                0,0,0,0,0,new TrustGate(score,40,70,85,200));
    }
    @Test void cautionMovesWithinPaymentBandAndReversesAfterBadEvidence() {
        String low = AgentContextAssembler.victimFunds(funds(45,0),true,45);
        String high = AgentContextAssembler.victimFunds(funds(55,0),true,45);
        String fallen = AgentContextAssembler.victimFunds(funds(45,0),true,55);
        assertTrue(low.contains("自行查到的渠道核验"));
        assertTrue(high.contains("普通疑问得到合理说明后可以不再独立核验"));
        assertTrue(high.contains("比上一轮更愿意相信"));
        assertTrue(fallen.contains("新出现的问题使你重新谨慎"));
        for (String text : List.of(low,high,fallen)) assertTrue(text.contains("愿意新增支付的总额最多为0元"));
    }
    @Test void willingnessUsesFrozenGatesAndAdditionalRiskIsNotAnAutomaticBonus() {
        assertTrue(AgentContextAssembler.victimFunds(funds(69,0),true,69).contains("总额最多为0元"));
        assertTrue(AgentContextAssembler.victimFunds(funds(70,0),true,69).contains("总额最多为200元"));
        assertTrue(AgentContextAssembler.victimFunds(funds(84,200),true,80).contains("总额最多为0元"));
        String large = AgentContextAssembler.victimFunds(funds(85,200),true,84);
        assertTrue(large.contains("总额最多为10000元"));
        assertTrue(large.contains("对新增金额、费用和条件重新关注风险"));
        assertTrue(large.contains("不预设必须继续或必须止损"));
        assertTrue(large.contains("台词说明一个来自已知事实的具体理由"));
        assertTrue(large.contains("本轮暂不追加不等于停止往来"));
        assertTrue(large.contains("可以抵触并拒绝追加"));
        assertTrue(large.contains("为拿回本金继续联系不代表仍然相信"));
    }
}
