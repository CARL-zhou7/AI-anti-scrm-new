package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate;
import com.aiscam.antiscam.simulation.rules.TrustRules;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import com.aiscam.antiscam.simulation.service.TrustEventService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrustInfluenceTest {
    private FundsContext funds(int score,int accept,int small,int large,int paid) {
        return new FundsContext(ScenarioCode.IMPERSONATION_LOAN,20000,80000,0,paid,0,List.of(),
                0,60000,20000,0,0,new TrustGate(score,accept,small,large,2000));
    }
    private String template(String file) throws Exception {
        try (var in=getClass().getResourceAsStream("/prompts/"+file+".txt")) {
            return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test void frozenThresholdsAndCurrentScoreChangeInclinationWithoutExposingTrustPolicy() {
        var old = funds(65,60,80,90,0);
        String oldText = AgentContextAssembler.victimFunds(old);
        assertTrue(oldText.contains("不涉及付款的具体事项"));
        assertTrue(AgentContextAssembler.victimFunds(funds(65,50,60,75,0)).contains("小额安排"));
        assertTrue(AgentContextAssembler.victimFunds(funds(75,50,60,75,0)).contains("较多资金"));
        assertTrue(AgentContextAssembler.victimFunds(funds(49,50,60,75,0)).contains("还不太愿意"));
        assertTrue(AgentContextAssembler.victimFunds(funds(50,50,60,75,0)).contains("不涉及付款的具体事项"));
        assertTrue(AgentContextAssembler.victimFunds(funds(60,50,60,75,2000)).contains("暂不继续增加资金投入"));
        assertEquals(oldText,AgentContextAssembler.victimFunds(old));
        for (int score : List.of(49,50,60,75,100)) {
            String inclination = AgentContextAssembler.victimFunds(funds(score,50,60,75,0)).split("【你此刻的判断倾向，仅用于形成自然回应】",2)[1];
            assertTrue(inclination.contains("不属于人物所知事实"));
            for (String hidden : List.of("信任分","阈值","系数","gate","score")) assertFalse(inclination.contains(hidden));
            assertTrue(inclination.contains("优先于背景里一般性的谨慎"));
            assertFalse(inclination.contains("不是必须付款"));
            assertTrue(inclination.contains("确切反证或明确停止往来仍应优先考虑"));
        }
    }

    @Test void identicalConversationUsesDifferentVictimPromptsAfterThresholdChange() throws Exception {
        var scene = new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"借款","简介","联系人","父亲","test",
                template("v9/scammer"),template("v12/victim"),template("v9/narrator"),"公开背景",true,template("v6/event"),true);
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> {
            prompts.add(request.prompt());
            return "{\"line\":\"你说说这笔钱具体用在哪\",\"action\":null,\"psychology\":\"考虑是否帮助\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"fundActions\":[]}";
        },new SimulationProperties().getLlm());
        var line = new DialogueLine(5,SpeakerRole.SCAMMER,"想先借一点钱周转",null,"不可披露意图");
        for (var context : List.of(funds(65,60,80,90,0),funds(65,50,60,75,0),funds(100,50,60,75,0))) {
            var turn = runner.victim(scene,List.of(),line,5,10000,false,0,context,"","");
            assertTrue(turn.fundActions().isEmpty());
        }
        assertTrue(prompts.get(0).contains("不涉及付款的具体事项"));
        assertTrue(prompts.get(1).contains("小额安排"));
        assertTrue(prompts.get(2).contains("较多资金"));
        assertFalse(prompts.get(2).contains("不可披露意图"));
        assertFalse(AgentContextAssembler.scammerFunds(funds(100,50,60,75,0)).contains("判断倾向"));
    }

    @Test void paymentIntentAndBudgetFollowGateWhilePaymentStillNeedsValidExecution() throws Exception {
        assertTrue(AgentContextAssembler.victimFunds(funds(59,50,60,70,0)).contains("总额最多为0元"));
        assertTrue(AgentContextAssembler.victimFunds(funds(60,50,60,70,0)).contains("总额最多为2000元"));
        assertTrue(AgentContextAssembler.victimFunds(funds(69,50,60,70,1500)).contains("总额最多为500元"));
        assertTrue(AgentContextAssembler.victimFunds(funds(70,50,60,70,0)).contains("总额最多为100000元"));
        var noEmergency = new FundsContext(ScenarioCode.IMPERSONATION_LOAN,10,150,0,0,0,List.of(),
                0,100,50,0,0,new TrustGate(60,50,60,70,2000));
        assertTrue(AgentContextAssembler.victimFunds(noEmergency).contains("总额最多为110元"));
        var scene = new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"借款","简介","联系人",
                "细致、习惯记账；一般谨慎，但涉及亲人时可能改变判断顺序。","test",
                template("v9/scammer"),template("v12/victim"),template("v9/narrator"),"公开背景",true,template("v6/event"),true);
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains("本轮就实际执行付款"));
            assertTrue(request.prompt().contains("不能因为此前说过要核实就永久坚持旧决定"));
            assertFalse(request.prompt().contains("不是必须付款"));
            return """
                    {"line":"我已实际转账12000元，你处理好了告诉我，爸记一下这笔账。",
                     "action":"已实际转账12000元","psychology":"决定先帮孩子周转","tone":{"speed":"NORMAL","style":"自然"},
                     "fundActions":[{"type":"PAYMENT","purpose":"LOAN_TRANSFER","amountYuan":12000,"roundNo":9,
                       "speaker":"VICTIM","quote":"已实际转账12000元","eventOrder":1,"reason":null,"relatedPaymentId":null,"reserveTier":null}]}
                    """;
        },new SimulationProperties().getLlm());
        var line = new DialogueLine(9,SpeakerRole.SCAMMER,"爸，借12000元处理事故，转到我这张模拟工资卡。",null,"");
        var turn = runner.victim(scene,List.of(),line,9,10000,false,0,funds(70,50,60,70,0),"","");
        assertEquals(12000,turn.fundActions().getFirst().amountYuan());
        assertThrows(AgentCallException.class, () -> runner.victim(scene,List.of(),line,9,10000,false,0,
                funds(69,50,60,70,0),"",""));
        assertNotNull(new db.migration.V30__make_trust_drive_victim_actions().getChecksum());
    }

    @Test void sixtyAndSeventyHaveDifferentCautionWithoutGrantingPayment() {
        String sixty=AgentContextAssembler.victimFunds(funds(60,55,75,90,0));
        String seventy=AgentContextAssembler.victimFunds(funds(70,55,75,90,0));
        assertTrue(sixty.contains("主动追问强度=0.1600"));
        assertTrue(seventy.contains("主动追问强度=0.0900"));
        assertTrue(sixty.contains("接纳解释倾向=0.8400"));
        assertTrue(seventy.contains("接纳解释倾向=0.9100"));
        assertTrue(sixty.contains("总额最多为0元"));
        assertTrue(seventy.contains("总额最多为0元"));
        assertFalse(sixty.contains("信任分"));
        assertFalse(seventy.contains("信任分"));
        assertNotEquals(seventy,AgentContextAssembler.victimFunds(funds(71,55,75,90,0)));
        assertNotNull(new db.migration.V34__progressive_victim_caution().getChecksum());
    }

    @Test void eightyAllowsTheNewDepositDespiteOrdinaryUnresolvedFeeDoubts() throws Exception {
        var context = new FundsContext(ScenarioCode.TASK_REBATE,3136,9000,0,0,0,List.of(),
                136,7500,1500,0,0,new TrustGate(80,30,60,70,100));
        var scene = new AgentScene(ScenarioCode.TASK_REBATE,"刷单返利","简介","联系人",
                "林晓，希望补充生活费；疑问未完全解决时也可能尝试，付款由实际交流推动。","test",
                template("v13/scammer"),template("v14/victim"),template("v10/narrator"),"公开背景",true,template("v6/event"),true);
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains("本轮已形成的付款决定"));
            assertTrue(request.prompt().contains("费用争议、等待返款或普通话术变化本身不能等同已识破"));
            assertTrue(request.prompt().contains("重新按本轮自己的资金事实和行动决定处理"));
            return """
                    {"line":"这笔120元我先垫了，之前说的35元差额你也记得补上。",
                     "action":"已实际支付120元保证金","psychology":"虽然还有小疑问，但收到报酬后愿意继续尝试",
                     "tone":{"speed":"NORMAL","style":"自然"},
                     "fundActions":[{"type":"PAYMENT","purpose":"INITIAL_PAYMENT","amountYuan":120,
                       "roundNo":20,"speaker":"VICTIM","quote":"已实际支付120元保证金","eventOrder":1,
                       "reason":null,"relatedPaymentId":null,"reserveTier":null}]}
                    """;
        },new SimulationProperties().getLlm());
        var history=List.of(new DialogueLine(19,SpeakerRole.SCAMMER,"这一单垫付我全出",null,""),
                new DialogueLine(19,SpeakerRole.VICTIM,"之前的35元还没补上",null,""));
        var line=new DialogueLine(20,SpeakerRole.SCAMMER,"第二单要先缴纳120元保证金到这个任务账户",null,"");
        var turn=runner.victim(scene,history,line,20,10000,false,0,context,"已收到报酬","");
        var after=com.aiscam.antiscam.simulation.rules.FundRules.apply(context.summary(),context,20,1,turn.fundActions()).after();
        assertEquals(120,after.totalPaidYuan());
        assertEquals(3016,after.availableBalanceYuan());
        assertEquals(com.aiscam.antiscam.simulation.rules.ActionGate.GateDecision.NEED_LARGE,
                com.aiscam.antiscam.simulation.rules.ActionGate.decide(new TrustGate(69,30,60,70,100),
                        turn.fundActions().getFirst(),0,3136,7500,1500));
        assertTrue(template("v13/scammer").contains("只承诺已经明确的本次体验范围"));
        assertNotNull(new db.migration.V37__align_payment_decisions_and_trial_scope().getChecksum());
    }

    @Test void availableMoneyIsNotReplacedByZeroPaymentWillingness() {
        var context = funds(65,55,75,90,0);
        String victim = AgentContextAssembler.victimFunds(context);
        assertTrue(victim.contains("当前可动用余额：20000元"));
        assertTrue(victim.contains("真实持有资金合计：100000元"));
        assertTrue(victim.contains("总额最多为0元"));
        assertTrue(victim.contains("付款意愿额度不是账户余额"));
        assertTrue(victim.contains("不得声称拿不出"));
        assertTrue(victim.contains("本轮纠正为暂不愿转"));
        assertTrue(AgentContextAssembler.victimFunds(funds(75,55,75,90,0)).contains("总额最多为2000元"));
        String narrator = AgentContextAssembler.narratorFunds(context,null,false);
        assertTrue(narrator.contains("当前余额：20000元"));
        assertTrue(narrator.contains("受害者说没钱不等于实际资金不足"));
        assertFalse(AgentContextAssembler.scammerFunds(context).contains("当前可动用余额"));
        assertNotNull(new db.migration.V32__separate_payment_willingness_from_funds().getChecksum());
    }

    @Test void eventContextIncludesPriorKeysButNotScoresOrCurrentRound() throws Exception {
        var repository = mock(SimulationTrustEventRepository.class);
        var past = new SimulationTrustEvent(64L,3,100L,
                new TrustRules.ScoredEvent(TrustEventType.NEW_EFFECTIVE_EXPLANATION,"material:通知",3,new BigDecimal("1.60"),5,70,75),
                "CONTENT","收到通知");
        var current = new SimulationTrustEvent(64L,5,101L,
                new TrustRules.ScoredEvent(TrustEventType.REPEAT_NO_PROGRESS,"material:通知",0,BigDecimal.ONE,0,75,75),
                "CONTENT","本轮不应重复输入");
        when(repository.findBySessionIdOrderByIdAsc(64L)).thenReturn(List.of(past,current));
        var service = new TrustEventService(null,null,null,repository,null,null);
        String facts = service.priorEventFacts(64L,5);
        assertTrue(facts.contains("material:通知"));
        assertTrue(facts.contains("收到通知"));
        assertFalse(facts.contains("本轮不应重复输入"));
        assertFalse(facts.contains("75"));
        var scene = new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"借款","简介","联系人","父亲","test",
                "unused","unused","unused","背景",true,template("v6/event"),true);
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains(facts));
            assertTrue(request.prompt().contains("新信息不等于有效解释"));
            return "{\"events\":[]}";
        },new SimulationProperties().getLlm());
        assertTrue(runner.event(scene,List.of(),5,10000,0,funds(75,50,60,75,0),facts).isEmpty());
        assertNotNull(new db.migration.V29__clarify_effective_trust_events().getChecksum());
    }
}
