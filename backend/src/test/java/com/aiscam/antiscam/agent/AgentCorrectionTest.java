package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentCorrectionTest {
    @Test void victimCorrectsRejectedPaymentToNoActionWhenTrustDoesNotPermitIt() {
        String payment = """
                {"line":"爸已转账五千元","action":null,"psychology":"担心孩子",
                 "tone":{"speed":"NORMAL","style":"自然"},
                 "fundActions":[{"type":"PAYMENT","purpose":"LOAN_TRANSFER","amountYuan":5000,
                   "roundNo":2,"speaker":"VICTIM","quote":"已转账五千元","eventOrder":1,
                   "reason":null,"relatedPaymentId":null,"reserveTier":null}]}
                """;
        String noPayment = """
                {"line":"先别急，手术的情况你再说清楚，我暂时不转。","action":null,"psychology":"担心但暂不付款",
                 "tone":{"speed":"NORMAL","style":"自然"},"fundActions":[]}
                """;
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> {
            prompts.add(request.prompt());
            return switch (prompts.size()) {
                case 1 -> payment;
                case 2 -> payment.replace("五千", "5000");
                default -> noPayment;
            };
        }, new SimulationProperties().getLlm());
        var scene = new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"借款","简介","联系人","父亲","test",
                "", template("v17/victim"), "", "公开背景", true, "", true);
        var funds = new FundsContext(ScenarioCode.IMPERSONATION_LOAN,20000,9000,0,0,0,List.of(),
                0,7500,1500,0,0,new com.aiscam.antiscam.simulation.rules.ActionGate.TrustGate(50,30,70,85,2000));
        var line = new DialogueLine(2,SpeakerRole.SCAMMER,"爸，马上手术急等五千块押金",null,null);
        var formatError = assertThrows(AgentCallException.class, () -> runner.victim(scene,List.of(),line,2,
                10000,false,0,funds,"",""));
        assertTrue(formatError.getMessage().contains("quote is missing the Arabic amount"));
        assertTrue(prompts.getFirst().contains("意愿额度为0时，fundActions必须为[]"));
        var permissionError = assertThrows(AgentCallException.class, () -> runner.victim(scene,List.of(),line,2,
                10000,false,1,funds,"",formatError.retryFeedback()));
        assertTrue(permissionError.getMessage().contains("fund action is not permitted"));
        assertTrue(prompts.get(1).contains("先核对本轮付款意愿和资金权限，再修正金额格式"));
        var corrected = runner.victim(scene,List.of(),line,2,10000,false,2,funds,"",permissionError.retryFeedback());
        assertTrue(corrected.fundActions().isEmpty());
        assertTrue(prompts.getLast().contains("台词和动作同步改为未执行"));
        assertEquals(0, com.aiscam.antiscam.simulation.rules.FundRules.apply(funds.summary(),funds,2,1,
                corrected.fundActions()).after().totalPaidYuan());
    }

    @Test void repeatedGeneratedScammerLineIsRejectedWithFeedbackAndCanBeCorrected() {
        String invitation = "想带您一起体验模拟盘？";
        String raw = "{\"line\":\"" + invitation + "\",\"action\":null,\"intent\":\"邀请体验\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"claimedRefundYuan\":null,\"fundActions\":[]}";
        var outputs = new java.util.ArrayDeque<>(List.of(raw, raw.replace(invitation, "可以，先看模拟盘的信号说明，不涉及真实入金。")));
        var runner = new AgentRunner(request -> outputs.remove(), new SimulationProperties().getLlm());
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                template("v19/scammer"), "", "", "公开背景", true, "", true, true);
        var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000,9000,0,0,0,List.of());
        var history = List.of(new DialogueLine(2,SpeakerRole.SCAMMER,invitation,null,null),
                new DialogueLine(2,SpeakerRole.VICTIM,"先看看信号逻辑",null,null));
        var error = assertThrows(AgentCallException.class, () -> runner.scammer(scene,history,3,10000,0,funds));
        assertTrue(error.retryable());
        assertTrue(error.retryFeedback().contains("不要重复上一轮台词"));
        var corrected = runner.scammer(scene,history,3,10000,1,funds,"",error.retryFeedback());
        assertNotEquals(invitation, corrected.line());
    }

    @Test void victimPromptDistinguishesFreeSimulationFromRequestsForRealFunds() {
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> {
            prompts.add(request.prompt());
            return "{\"line\":\"可以先看看模拟盘，怎么体验？\",\"action\":null,\"psychology\":\"愿意了解\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"fundActions\":[]}";
        }, new SimulationProperties().getLlm());
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                "", template("v17/victim"), "", "公开背景", true, "", true);
        runner.victim(scene,List.of(),new DialogueLine(2,SpeakerRole.SCAMMER,"想带您一起体验模拟盘？",null,null),
                2,10000,false,0,new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000,9000,0,0,0,List.of()));
        assertTrue(prompts.getFirst().contains("不要把模拟体验改读为实盘投资"));
        assertTrue(prompts.getFirst().contains("历史已有未解决的真实付款要求时仍可回应"));
    }

    @Test void misplacedNullReserveTierIsHarmlessButInstructionsAndOtherExtraFieldsStillFail() {
        String raw="{\"line\":\"先看看说明\",\"action\":null,\"intent\":\"介绍\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"claimedRefundYuan\":null,\"fundActions\":[],\"reserveTier\":null}";
        assertTrue(AgentOutputParser.scammer(raw).fundActions().isEmpty());
        assertTrue(AgentOutputParser.scammer(raw.replace("reserveTier", "relatedPaymentId")).fundActions().isEmpty());
        assertThrows(AgentCallException.class, () -> AgentOutputParser.scammer(raw.replace("\"reserveTier\":null", "\"relatedPaymentId\":123")));
        assertThrows(AgentCallException.class,()->AgentOutputParser.scammer(raw.replace("\"reserveTier\":null","\"reserveTier\":\"GENERAL\"")));
        assertThrows(AgentCallException.class,()->AgentOutputParser.scammer(raw.replace("reserveTier","unknown")));
    }

    @Test void eventContextDoesNotExposeInternalIntentAsObservableEvidence() {
        var scene=new AgentScene(ScenarioCode.TASK_REBATE,"刷单","简介","联系人","本人","test","","","","公开",true,"",true,true);
        var history=List.of(new DialogueLine(1,SpeakerRole.VICTIM,"需要垫钱吗",null,"PRIVATE_PSYCHOLOGY"),
                new DialogueLine(2,SpeakerRole.SCAMMER,"首单我垫付",null,"PRIVATE_FORGERY"));
        String context=String.join("\n",AgentContextAssembler.event(scene,history,2,null).values());
        assertFalse(context.contains("PRIVATE_"));assertTrue(context.contains("需要垫钱吗"));assertTrue(context.contains("首单我垫付"));
    }

    @Test void eventQuotesRejectHistoryOtherSpeakerInternalNotesAndParaphrasesBeforePersistence() throws Exception {
        var scene=new AgentScene(ScenarioCode.FAKE_INVESTMENT,"投资","简介","联系人","本人","test",
                "","","","公开背景",true,template("v17/event"),true,true);
        var history=List.of(new DialogueLine(5,SpeakerRole.SCAMMER,"HISTORY_ONLY",null,null),
                new DialogueLine(6,SpeakerRole.VICTIM,"VICTIM_ONLY",null,null),
                new DialogueLine(6,SpeakerRole.SCAMMER,"现在注册即可查看持仓","发送《观盘指引》PDF","PRIVATE_INTENT"));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String quote:List.of("HISTORY_ONLY","VICTIM_ONLY","PRIVATE_INTENT","注册以后就能查看持仓")) {
            String raw=mapper.writeValueAsString(java.util.Map.of("events",List.of(java.util.Map.of(
                    "type","NEW_EFFECTIVE_EXPLANATION","subjectKey","registration","progressKey","registration:1","quote",quote,"source","CONTENT"))));
            var runner=new AgentRunner(request->raw,new SimulationProperties().getLlm());
            var error=assertThrows(AgentCallException.class,()->runner.event(scene,history,6,10000,0,null));
            assertTrue(error.retryFeedback().contains(raw));assertFalse(error.getMessage().contains(quote));
            assertTrue(error.getMessage().contains("source=CONTENT"));
        }
    }

    @Test void familyIdentityJudgementDoesNotUseFatherOrScammerNameAsAnUnspecifiedSonName() {
        var prompts=new ArrayList<String>();
        var runner=new AgentRunner(request->{prompts.add(request.prompt());return "{\"events\":[]}";},new SimulationProperties().getLlm());
        var scene=new AgentScene(ScenarioCode.IMPERSONATION_LOAN,"借款","简介","陈启明","父亲姓名周国良；儿子随父姓，全名未设定","test",
                "","","","公开背景",true,template("v17/event"),true,true);
        var lines=List.of(new DialogueLine(2,SpeakerRole.SCAMMER,"爸，我是小周","发送标注周小周-医院缴费的二维码",null));
        assertTrue(runner.event(scene,lines,2,10000,0,null).isEmpty());
        assertTrue(prompts.getFirst().contains("父亲姓名不等于儿子姓名"));
        assertTrue(prompts.getFirst().contains("周小周"));
        assertTrue(prompts.getFirst().contains("矛盾双方"));
    }

    @Test void clickingInvestmentLinkCannotBecomePaymentEvidenceWhenLedgerHasNoPayments() {
        var lines = List.of(new DialogueLine(4,SpeakerRole.VICTIM,"100元体验我先试试，链接我点进去了。",null,null));
        var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000,9000,0,0,0,List.of());
        for (var code : List.of("INITIAL_PAYMENT_COMPLETED","ADDITIONAL_PAYMENT_COMPLETED","INVESTMENT_COMPLETED",
                "EXTRA_FEE_PAID","LOAN_TRANSFER_COMPLETED")) {
            var evidence = new EvidenceItem(com.aiscam.antiscam.simulation.rules.FactCode.valueOf(code),SpeakerRole.VICTIM,
                    EvidenceSource.CONTENT,4,lines.getFirst().content(),1);
            var judgement = new NarratorJudgement("诱导体验","诱导点击","小额诱导","愿意尝试",75,"已点击，尚未付款",
                    true,false,false,List.of(evidence),null,null,false,null);
            var error = assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(judgement,
                    ScenarioCode.FAKE_INVESTMENT,4,lines,PriorFacts.none(),funds.summary()));
            assertTrue(error.retryFeedback().contains("累计实际付款为0"));
            assertTrue(error.retryFeedback().contains("删除付款类evidence"));
        }
        var corrected = new NarratorJudgement("诱导体验","诱导点击","小额诱导","愿意尝试",75,"已点击，尚未付款",
                false,false,false,List.of(),null,null,false,null);
        assertDoesNotThrow(() -> AgentOutputValidator.validate(corrected,ScenarioCode.FAKE_INVESTMENT,4,lines,PriorFacts.none(),funds.summary()));
        var payment = new NarratorJudgement("诱导体验","诱导付款","小额诱导","已经付款",100,"已付款",
                true,false,false,List.of(new EvidenceItem(com.aiscam.antiscam.simulation.rules.FactCode.INVESTMENT_COMPLETED,
                SpeakerRole.VICTIM,EvidenceSource.ACTION,4,"已转账100元",1)),null,null,false,null);
        assertDoesNotThrow(() -> AgentOutputValidator.validate(payment,ScenarioCode.FAKE_INVESTMENT,4,
                List.of(new DialogueLine(4,SpeakerRole.VICTIM,"转好了","已转账100元",null)),PriorFacts.none(),
                new FundsContext(ScenarioCode.FAKE_INVESTMENT,2900,9000,0,100,0,List.of()).summary()));
    }

    @Test void narratorReceivesExplicitPaymentTotalBeforeItJudgesDialogue() {
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> { prompts.add(request.prompt()); return "{}"; },new SimulationProperties().getLlm());
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT,"场景","简介","联系人","本人","test",
                "","",template("v17/narrator"),"公开背景",true,"",true);
        for (int paid : List.of(0,100)) {
            var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000-paid,9000,0,paid,0,List.of());
            assertThrows(AgentCallException.class, () -> runner.narrator(scene,List.of(),4,PriorFacts.none(),10000,0,
                    funds,null,false,List.of()));
            assertTrue(prompts.getLast().contains("累计实际付款=" + paid + "元"));
            assertTrue(prompts.getLast().contains("点击链接不等于付款"));
        }
    }

    @Test void verificationIntentIsRejectedWithActionableFeedbackAndCanBeCorrectedWithoutInventingActions() {
        String raw = """
            {"fraudStage":"建立信任","speechAnalysis":"以认证截图证明可信","methodAnalysis":"展示平台截图",
            "victimPsychology":"仍有疑虑","riskScore":25,"riskReason":"尚未付款，提出核实意向",
            "actualFraudOccurred":false,"goalAchieved":false,"cooperationStopped":false,
            "evidence":[],"candidateOutcome":null,"candidateReason":null,
            "waitingUpdates":[{"action":"OPEN","type":"VERIFICATION","subjectKey":"verify:备案",
            "text":"查平台备案","basis":"这平台备案号我查一下监管网站"}]}
            """;
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> { prompts.add(request.prompt()); return prompts.size() == 1 ? raw
                : raw.substring(0, raw.indexOf("\"waitingUpdates\"")) + "\"waitingUpdates\":[]}"; }, new SimulationProperties().getLlm());
        var scene = new AgentScene(ScenarioCode.FAKE_INVESTMENT,"场景","简介","联系人","本人","test",
                "", "", template("v17/narrator"),"公开背景",true,"",true);
        var funds = new FundsContext(ScenarioCode.FAKE_INVESTMENT,3000,9000,0,0,0,List.of());
        var lines = List.of(new DialogueLine(2,SpeakerRole.SCAMMER,"这是平台截图",null,null),
                new DialogueLine(2,SpeakerRole.VICTIM,"这平台备案号我查一下监管网站",null,null));
        var error = assertThrows(AgentCallException.class, () -> runner.narrator(scene,lines,2,PriorFacts.none(),
                10000,0,funds,null,false,List.of(),"",""));
        assertTrue(error.retryFeedback().contains("waitingUpdates=[]"));
        assertTrue(error.retryFeedback().contains("不得编造"));
        var corrected = assertDoesNotThrow(() -> runner.narrator(scene,lines,2,PriorFacts.none(),
                10000,1,funds,null,false,List.of(),"",error.retryFeedback()));
        assertTrue(corrected.judgement().waitingUpdates().isEmpty());
        assertTrue(prompts.getLast().contains("我查一下"));
        var executed = raw.replace("这平台备案号我查一下监管网站","已拨打官方电话核实平台备案");
        assertDoesNotThrow(() -> AgentOutputValidator.validate(AgentOutputParser.narrator(executed),ScenarioCode.FAKE_INVESTMENT,2,
                List.of(new DialogueLine(2,SpeakerRole.VICTIM,"已拨打官方电话核实平台备案",null,null)),PriorFacts.none()));
    }

    private String template(String file) {
        try (var in = getClass().getResourceAsStream("/prompts/" + file + ".txt")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @Test void allFourRolesRetainOnlyRejectedOutputInRetryFeedbackNotLogMessage() {
        String raw = "{\"unexpected\":\"PRIVATE_OUTPUT_SENTINEL\"}";
        var prompts = new ArrayList<String>();
        var runner = new AgentRunner(request -> { prompts.add(request.prompt()); return raw; }, new SimulationProperties().getLlm());
        var scene = new AgentScene(ScenarioCode.TASK_REBATE,"场景","简介","联系人","本人","test",
                template("v9/scammer"),template("v7/victim"),template("v7/narrator"),"公开背景",true,template("v5/event"),true);
        var funds = new FundsContext(ScenarioCode.TASK_REBATE,3000,9000,0,0,0,List.of());
        var line = new DialogueLine(5,SpeakerRole.SCAMMER,"继续聊",null,null);
        var history = List.of(line);
        List<Function<String,Object>> calls = List.of(
                feedback -> runner.scammer(scene,history,5,10000,0,funds,"",feedback),
                feedback -> runner.victim(scene,List.of(),line,5,10000,false,0,funds,"",feedback),
                feedback -> runner.event(scene,history,5,10000,0,funds,"",feedback),
                feedback -> runner.narrator(scene,history,5,PriorFacts.none(),10000,0,funds,null,false,List.of(),"",feedback));
        for (var call : calls) {
            var error = assertThrows(AgentCallException.class, () -> call.apply(""));
            assertFalse(error.getMessage().contains("PRIVATE_OUTPUT_SENTINEL"));
            assertTrue(error.retryFeedback().contains(raw));
            assertThrows(AgentCallException.class, () -> call.apply(error.retryFeedback()));
            assertTrue(prompts.getLast().contains(raw));
        }
        assertFalse(new AgentCallException(AgentCallException.Category.DIALOGUE,true,"model call failed")
                .retryFeedback().contains("PRIVATE_OUTPUT_SENTINEL"));
    }

    @Test void fieldDiagnosticsNameMissingAndExtraKeysWithoutIncludingTheirValues() {
        var error = assertThrows(AgentCallException.class, () -> AgentOutputParser.scammer(
                "{\"line\":\"继续聊\",\"action\":null,\"psychology\":\"PRIVATE_VALUE\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"claimedRefundYuan\":null,\"fundActions\":[]}"));
        assertTrue(error.getMessage().contains("missing fields: intent; extra fields: psychology"));
        assertFalse(error.getMessage().contains("PRIVATE_VALUE"));
    }

    @Test void invalidToneGetsAllowedValuesAndWaitingEvidenceFailsBeforePersistence() {
        var error = assertThrows(AgentCallException.class, () -> AgentOutputParser.victim(
                "{\"line\":\"你好\",\"action\":null,\"psychology\":\"平静\",\"tone\":{\"speed\":\"OTHER\",\"style\":\"自然\"}}"));
        assertTrue(error.getMessage().contains("Speed field speed"));
        assertTrue(error.getMessage().contains("NORMAL"));
        var update = new WaitingUpdate(WaitingAction.OPEN,WaitingItemType.VERIFICATION,"verify:bank","核实账户","已拨打银行官方电话核实账户");
        var judgement = new NarratorJudgement("核实","核实","核实","犹豫",25,"保持警惕",false,false,false,
                List.of(),null,null,false,null,List.of(),List.of(update));
        var mismatch = assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(judgement,
                ScenarioCode.TASK_REBATE,5,List.of(new DialogueLine(5,SpeakerRole.VICTIM,"我再考虑一下",null,null)),PriorFacts.none()));
        assertTrue(mismatch.getMessage().contains("waiting basis"));
        assertDoesNotThrow(() -> AgentOutputValidator.validate(judgement,ScenarioCode.TASK_REBATE,5,
                List.of(new DialogueLine(5,SpeakerRole.VICTIM,"正在核实",update.basis(),null)),PriorFacts.none()));
    }
}
