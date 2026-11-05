package com.aiscam.antiscam.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VictimGuidanceAgentTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(ScenarioCode.class)
    void matcherRejectsForeignPurposeWithCurrentScenarioChoicesAndCanRetry(ScenarioCode scenario) {
        var allowed = java.util.Arrays.stream(FundPurpose.values()).filter(p -> FundRules.paymentPurposeAllowed(scenario, p)).toList();
        var wrong = scenario == ScenarioCode.IMPERSONATION_LOAN ? FundPurpose.INVESTMENT_PAYMENT : FundPurpose.LOAN_TRANSFER;
        String raw = """
                {"ruleId":"4","sameRequestRound":0,"requestSource":"CONTENT","amountYuan":500,"purpose":"%s",
                 "recipient":"模拟收款人","previousResponse":"NONE","responseSource":"NONE","blocked":false,"blockingQuote":""}
                """;
        var prompt = new AtomicReference<String>();
        var output = new AtomicReference<>(raw.formatted(wrong));
        var runner = new AgentRunner(request -> { prompt.set(request.prompt()); return output.get(); }, new SimulationProperties().getLlm());
        var scene = new AgentScene(scenario,"场景","说明","联系人","本人","test","","","","本人",true,"",true);
        var current = new DialogueLine(7, SpeakerRole.SCAMMER, "请转500元给模拟收款人", null, null);
        var error = assertThrows(AgentCallException.class, () -> runner.victimRuleMatch(scene,"规则","[]",List.of(),current,7,60000,0,""));
        assertTrue(error.retryFeedback().contains("本场只允许=" + allowed));
        assertTrue(error.retryFeedback().contains("错误purpose=" + wrong));
        assertTrue(error.retryFeedback().contains(raw.formatted(wrong)));
        output.set(raw.formatted(allowed.getFirst()));
        assertEquals(allowed.getFirst(), runner.victimRuleMatch(scene,"规则","[]",List.of(),current,7,60000,1,error.retryFeedback()).purpose());
        assertTrue(prompt.get().contains("本场允许付款用途（程序资金规则）：" + allowed));
    }

    @Test void acceptedRequestWithoutRecipientMustAskForItInsteadOfRepeatingRefusal() throws Exception {
        String template;
        try (var in = getClass().getResourceAsStream("/prompts/v17/victim.txt")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT, "虚假投资", "说明", "联系人", "本人", "default",
                "unused", template, "unused", "本人", true, "unused", true, false, true);
        var decision = new VictimGuidance(12, "4", "4:10", "充值500元", 500,
                FundPurpose.INVESTMENT_PAYMENT, "", 3, true, false, false, "重复拒绝实盘投入后配合");
        var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT, 1000, 0, 0, 0, 0, List.of(),
                0, 0, 0, 0, 0, new ActionGate.TrustGate(37, 40, 70, 85, 1000), decision);
        var output = new AtomicReference<>(new VictimTurn("模拟盘我还在试，这500元我先不操作，等摸熟再考虑。",
                null, "暂缓", new ToneHint(Speed.NORMAL, "自然"), List.of()));
        var json = new ObjectMapper();
        var runner = new AgentRunner(request -> {
            try { return json.writeValueAsString(output.get()); } catch (Exception e) { throw new RuntimeException(e); }
        }, new SimulationProperties().getLlm());
        var current = new DialogueLine(12, SpeakerRole.SCAMMER, "现在充值500元就能解锁提现权限", null, null);
        var error = assertThrows(AgentCallException.class, () -> runner.victim(scene, List.of(), current, 12, 60000, false, 0, funds));
        assertTrue(error.retryFeedback().contains("收款对象"));
        assertTrue(decision.prompt(funds).contains("已决定接受本笔请求"));
        output.set(new VictimTurn("好，这500元充值到哪个模拟账户？", null, "接受安排，询问缺少的收款对象",
                new ToneHint(Speed.NORMAL, "自然"), List.of()));
        var accepted = runner.victim(scene, List.of(), current, 12, 60000, false, 1, funds);
        assertTrue(accepted.fundActions().isEmpty()); assertFalse(decision.paymentAllowed(funds));
        var clarified = new DialogueLine(13, SpeakerRole.SCAMMER, "向模拟商户充值500元", null, null);
        var next = VictimGuidance.decide(13, new VictimGuidance.Match("4", 12, clarified.content(), 500,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", "ACCEPTED", accepted.line(), false, ""),
                List.of(new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot("4", "配合", "接受本笔请求", 1, "VICTIM")),
                List.of(decision), List.of(new DialogueLine(12, SpeakerRole.VICTIM, accepted.line(), null, null)), clarified, false);
        assertEquals(decision.requestId(), next.requestId()); assertEquals(3, next.consecutive());
        var pay = new FundAction(FundFlowType.PAYMENT, FundPurpose.INVESTMENT_PAYMENT, 500, 13,
                SpeakerRole.VICTIM, "已实际转账500元给模拟商户", 1, null, null, null);
        output.set(new VictimTurn("好，按刚才说的办。", pay.quote(), "接受安排",
                new ToneHint(Speed.NORMAL, "自然"), List.of(pay)));
        assertEquals(500, runner.victim(scene, List.of(), clarified, 13, 60000, false, 0,
                funds.withGuidance(next)).fundActions().getFirst().amountYuan());
    }

    @Test void generationRejectsVerbalAcceptanceAndAcceptsOnlyTheCorrespondingRealAction() throws Exception {
        String template;
        try (var in = getClass().getResourceAsStream("/prompts/v17/victim.txt")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT, "虚假投资", "说明", "联系人", "本人", "default",
                "unused", template, "unused", "本人", true, "unused", true, false, true);
        var decision = new VictimGuidance(9, "4", "4:7", "请转1000元给模拟商户", 1000,
                FundPurpose.INVESTMENT_PAYMENT, "模拟商户", 3, true, false, false, "接受并执行当前请求");
        var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT, 1000, 0, 0, 0, 0, List.of(),
                0, 0, 0, 0, 0, new ActionGate.TrustGate(20, 40, 70, 85, 1000), decision);
        var output = new AtomicReference<>(new VictimTurn("好，我考虑一下", null, "仍在考虑", new ToneHint(Speed.NORMAL, "自然"), List.of()));
        var prompt = new AtomicReference<String>();
        var json = new ObjectMapper();
        var runner = new AgentRunner(request -> {
            prompt.set(request.prompt());
            try { return json.writeValueAsString(output.get()); } catch (Exception e) { throw new RuntimeException(e); }
        }, new SimulationProperties().getLlm());
        var current = new DialogueLine(9, SpeakerRole.SCAMMER, decision.requestQuote(), null, null);
        assertThrows(AgentCallException.class, () -> runner.victim(scene, List.of(), current, 9, 60000, false, 0, funds));
        assertTrue(prompt.get().contains("愿意新增支付的总额最多为1000元"));
        assertTrue(prompt.get().contains("本事项已经决定相信并付款"));
        var pay = new FundAction(FundFlowType.PAYMENT, FundPurpose.INVESTMENT_PAYMENT, 1000, 9,
                SpeakerRole.VICTIM, "已实际转账1000元", 1, null, null, null);
        output.set(new VictimTurn("好，这笔我先照你说的办", "已实际转账1000元给模拟商户", "决定接受这笔安排",
                new ToneHint(Speed.NORMAL, "自然"), List.of(pay)));
        var accepted = runner.victim(scene, List.of(), current, 9, 60000, true, 1, funds);
        assertEquals(1000, accepted.fundActions().getFirst().amountYuan());
        assertEquals(20, funds.gate().score());
    }

    @Test void requestEvidenceIsBoundToTheSelectedCurrentSourceInsteadOfModelRewriting() {
        var current = new DialogueLine(5, SpeakerRole.SCAMMER, "您转我卡上就行", "发送8000元押金截图", null);
        String raw = """
                {"ruleId":"4","sameRequestRound":4,"requestSource":"CONTENT","amountYuan":8000,"purpose":"LOAN_TRANSFER",
                 "recipient":"模拟收款人","previousResponse":"REFUSED","responseSource":"CONTENT", "blocked":false,"blockingQuote":""}
                """;
        var history = List.of(new DialogueLine(4, SpeakerRole.VICTIM, "暂不转，先确认用途。", "已查看材料", "PRIVATE"));
        assertEquals(current.content(), VictimGuidance.parseRequest(raw, current, history).requestQuote());
        assertEquals(current.action(), VictimGuidance.parseRequest(raw.replace("\"requestSource\":\"CONTENT\"", "\"requestSource\":\"ACTION\""), current, history).requestQuote());
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw.replace("CONTENT", "HISTORY"), current, history));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw.replace("CONTENT", "ACTION"),
                new DialogueLine(5, SpeakerRole.SCAMMER, current.content(), null, null), history));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw.replace("\"requestSource\":\"CONTENT\"", "\"requestQuote\":\"历史请求\""), current, history));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw + "{}", current, history));
        assertEquals(history.getFirst().content(), VictimGuidance.parseRequest(raw, current, history).responseQuote());
        assertEquals(history.getFirst().action(), VictimGuidance.parseRequest(raw.replace("\"responseSource\":\"CONTENT\"", "\"responseSource\":\"ACTION\""), current, history).responseQuote());
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw, current, List.of()));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw, current,
                List.of(new DialogueLine(4, SpeakerRole.SCAMMER, "暂不转", null, null))));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw.replace("\"responseSource\":\"CONTENT\"", "\"responseSource\":\"ACTION\""), current,
                List.of(new DialogueLine(4, SpeakerRole.VICTIM, "暂不转", null, null))));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parseRequest(raw.replace("\"responseSource\":\"CONTENT\"", "\"responseQuote\":\"改写的回应\""), current, history));
    }

    @Test void boundPreviousResponsePassesDecisionEvidenceValidation() {
        var response = new DialogueLine(9, SpeakerRole.VICTIM, "这笔投入先等等，我想再了解一下。", null, "PRIVATE");
        var current = new DialogueLine(10, SpeakerRole.SCAMMER, "请投入80元到投资平台账户", null, null);
        var prior = new VictimGuidance(9, "4", "4:9", current.content(), 80,
                FundPurpose.INVESTMENT_PAYMENT, "投资平台账户", 1, false, false, false, "同请求指导");
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT, "投资", "", "", "", "test",
                "", "", "", "", true, "", true, false, true);
        var runner = new AgentRunner(request -> {
            assertTrue(request.prompt().contains("程序直接提取原文"));
            assertFalse(request.prompt().contains("PRIVATE"));
            return """
                    {"ruleId":"4","sameRequestRound":9,"requestSource":"CONTENT","amountYuan":80,
                     "purpose":"INVESTMENT_PAYMENT","recipient":"投资平台账户","previousResponse":"REFUSED",
                     "responseSource":"CONTENT","blocked":false,"blockingQuote":""}
                    """;
        }, new SimulationProperties().getLlm());
        var match = runner.victimRuleMatch(scene, "规则", "已保存请求", List.of(response), current, 10, 60000, 0, "");
        assertEquals(response.content(), match.responseQuote());
        var decision = VictimGuidance.decide(10, match,
                List.of(new com.aiscam.antiscam.simulation.snapshot.SceneRuleSnapshot("4", "同请求", "同请求指导", 1, "VICTIM")),
                List.of(prior), List.of(response), current, false);
        assertEquals(2, decision.consecutive());
        assertFalse(decision.required());
    }

    @Test void identificationRejectsDuplicateFieldsWrongTypesAndTrailingObjects() {
        String valid = """
                {"ruleId":null,"sameRequestRound":0,"requestQuote":"","amountYuan":0,"purpose":null,
                 "recipient":"","previousResponse":"NONE","responseQuote":"","blocked":false,"blockingQuote":""}
                """;
        assertNull(VictimGuidance.parse(valid).ruleId());
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parse(valid + "{}"));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parse(valid.replace("\"amountYuan\":0", "\"amountYuan\":0,\"amountYuan\":1000")));
        assertThrows(IllegalArgumentException.class, () -> VictimGuidance.parse(valid.replace("\"blocked\":false", "\"blocked\":\"false\"")));
    }
}
