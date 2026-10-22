package com.aiscam.antiscam.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.media.SentenceSegmenter;
import com.aiscam.antiscam.media.SilentTtsClient;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRulesTest {
    private static final String NARRATOR = """
            {"fraudStage":"a","speechAnalysis":"b","methodAnalysis":"c","victimPsychology":"d","riskScore":%d,"riskReason":"e",
             "actualFraudOccurred":false,"goalAchieved":false,"cooperationStopped":%b,"evidence":%s,
             "candidateOutcome":%s,"candidateReason":%s}""";

    private static AgentScene scene() {
        return new AgentScene(ScenarioCode.TASK_REBATE, "刷单返利诈骗", "简介", "骗子画像", "受害者画像", "x",
                read("scammer"), read("victim"), read("narrator"), "公开经历", false);
    }

    private static String read(String name) {
        try (var in = AgentRulesTest.class.getResourceAsStream("/prompts/v1/" + name + ".txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private final List<DialogueLine> lines = List.of(
            new DialogueLine(5, SpeakerRole.SCAMMER, "快转账吧", null, "SCAMMER-INTENT-XYZ"),
            new DialogueLine(5, SpeakerRole.VICTIM, "我不会再理你了", "已经拉黑了对方", "VICTIM-MIND-XYZ"));

    @Test
    void victimFundsParsingKeepsLegacyAndRejectsMalformedAmounts() {
        String old = "{\"line\":\"已转100元\",\"action\":null,\"psychology\":\"试试看\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"\"}}";
        assertTrue(AgentOutputParser.victim(old).fundActions().isEmpty());
        String action = "{\"type\":\"PAYMENT\",\"purpose\":\"INITIAL_PAYMENT\",\"amountYuan\":100,\"roundNo\":1,\"speaker\":\"VICTIM\",\"quote\":\"已转100元\",\"eventOrder\":1,\"reason\":null,\"relatedPaymentId\":null}";
        String current = old.substring(0, old.length() - 1) + ",\"fundActions\":[" + action + "]}";
        var turn = AgentOutputValidator.validate(AgentOutputParser.victim(current));
        assertEquals(1, turn.fundActions().size());
        assertEquals(100, turn.fundActions().getFirst().amountYuan());
        for (String amount : List.of("1.5", "2147483648", "\"100\"", "null")) {
            assertThrows(AgentCallException.class, () -> AgentOutputParser.victim(current.replace("\"amountYuan\":100", "\"amountYuan\":" + amount)));
        }
        assertThrows(AgentCallException.class, () -> AgentOutputParser.victim(current.replace("INITIAL_PAYMENT", "UNKNOWN")));
        assertThrows(AgentCallException.class, () -> AgentOutputParser.victim(current + "{}"));
        assertThrows(AgentCallException.class, () -> AgentOutputParser.victim(current.replace("\"amountYuan\":100", "\"amountYuan\":100,\"amountYuan\":200")));
    }

    @Test
    void refundClaimsQuotesRolesAndContextBoundariesAreValidated() {
        var payment = new FundFlow(1, 1, 1L, new FundPosting(FundFlowType.PAYMENT, FundPurpose.INITIAL_PAYMENT, 100, 1, 900, null, null), Instant.EPOCH);
        ReflectionTestUtils.setField(payment, "id", 772L);
        var funds = new FundsContext(ScenarioCode.TASK_REBATE, 900, 400, 0, 100, 0, List.of(payment));
        String quote = "先返你50元，剩下的稍后到账";
        var refund = new FundAction(FundFlowType.REFUND, FundPurpose.REFUND, 50, 3, SpeakerRole.SCAMMER, quote, 1, null, 772L);
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, quote, null, 500, List.of(refund), 3, funds, List.of()));
        for (Integer claim : java.util.Arrays.asList(null, 49)) assertThrows(AgentCallException.class,
                () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, quote, null, claim, List.of(refund), 3, funds, List.of()));
        assertDoesNotThrow(() -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, "会返你500元", null, 500, List.of(), 3, funds, List.of()));
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.VICTIM, quote, null, null, List.of(refund), 3, funds, List.of()));
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, "已经返了款", null, 500, List.of(refund), 3, funds, List.of()));
        for (String promise : List.of("我会返你50元", "尚未转账50元", "以后返50元")) {
            var unexecuted = new FundAction(FundFlowType.REFUND, FundPurpose.REFUND, 50, 3, SpeakerRole.SCAMMER, promise, 1, null, 772L);
            assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, promise, null, 500, List.of(unexecuted), 3, funds, List.of()));
        }
        var loan = new FundsContext(ScenarioCode.IMPERSONATION_LOAN, 900, 400, 0, 100, 0, List.of(payment));
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.SCAMMER, quote, null, 500, List.of(), 3, loan, List.of()));
        for (String old : List.of("之前转了100元", "准备转账100元")) {
            var a = new FundAction(FundFlowType.PAYMENT, FundPurpose.OTHER_PAYMENT, 100, 3, SpeakerRole.VICTIM, old, 1, null, null);
            assertThrows(AgentCallException.class, () -> AgentOutputValidator.funds(SpeakerRole.VICTIM, old, null, null, List.of(a), 3, funds, List.of()));
        }
        String scammer = AgentContextAssembler.scammerFunds(funds), victim = AgentContextAssembler.victimFunds(funds);
        assertFalse(scammer.contains("900")); assertFalse(scammer.contains("400")); assertFalse(scammer.contains("亏损"));
        assertFalse(victim.contains("772")); assertFalse(victim.contains("追加")); assertFalse(victim.contains("亏损"));
        assertTrue(scammer.contains("772")); assertTrue(victim.contains("900"));
    }

    @Test
    void riskScore10IsRejectedNotRounded() {
        var j = AgentOutputParser.narrator(NARRATOR.formatted(10, false, "[]", "null", "null"));
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(j, ScenarioCode.TASK_REBATE, 5, lines, PriorFacts.none()));
    }

    @Test
    void stopNeedsVerbatimVictimEvidenceAndMatchingCandidate() {
        String evidence = "[{\"factCode\":\"COOPERATION_STOPPED\",\"speaker\":\"VICTIM\",\"source\":\"ACTION\",\"roundNo\":5,\"quote\":\"已经拉黑了对方\",\"eventOrder\":1}]";
        var ok = AgentOutputParser.narrator(NARRATOR.formatted(0, true, evidence, "\"NOT_DEFRAUDED\"", "\"拉黑\""));
        assertNotNull(AgentOutputValidator.validate(ok, ScenarioCode.TASK_REBATE, 5, lines, PriorFacts.none()));
        var wrongQuote = AgentOutputParser.narrator(NARRATOR.formatted(0, true, evidence.replace("已经拉黑了对方", "删除了"), "\"NOT_DEFRAUDED\"", "\"拉黑\""));
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(wrongQuote, ScenarioCode.TASK_REBATE, 5, lines, PriorFacts.none()));
        var undetermined = NARRATOR.formatted(0, true, evidence, "\"UNDETERMINED\"", "\"x\"");
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(AgentOutputParser.narrator(undetermined),
                ScenarioCode.TASK_REBATE, 5, lines, PriorFacts.none()));
    }

    @Test
    void extraFieldsAndActionNarrationInLineAreRejected() {
        assertThrows(AgentCallException.class, () -> AgentOutputParser.scammer(
                "{\"line\":\"x\",\"action\":null,\"intent\":\"i\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"\"},\"riskScore\":0}"));
        var turn = AgentOutputParser.scammer("{\"line\":\"好的（点击链接）\",\"action\":null,\"intent\":\"i\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"\"}}");
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(turn));
    }

    @Test
    void contextIsolation() {
        var history = List.of(new DialogueLine(1, SpeakerRole.SCAMMER, "你好", null, "SCAMMER-INTENT-XYZ"),
                new DialogueLine(1, SpeakerRole.VICTIM, "你是谁", null, "VICTIM-MIND-XYZ"));
        String victimPrompt = PromptRenderer.render(scene().victimTemplate(),
                AgentContextAssembler.victim(scene(), history, new DialogueLine(2, SpeakerRole.SCAMMER, "说说", null, "SCAMMER-INTENT-XYZ"), 2, 500, false));
        assertFalse(victimPrompt.contains("SCAMMER-INTENT-XYZ"));
        assertFalse(victimPrompt.contains("追加投入"), "victim never sees the scenario goal");
        assertTrue(victimPrompt.contains("前4轮行为约束"));
        String scammerPrompt = PromptRenderer.render(scene().scammerTemplate(), AgentContextAssembler.scammer(scene(), history, 2, 500));
        assertFalse(scammerPrompt.contains("VICTIM-MIND-XYZ"));
        String narratorPrompt = PromptRenderer.render(scene().narratorTemplate(), AgentContextAssembler.narrator(scene(), history, 1, PriorFacts.none()));
        assertTrue(narratorPrompt.contains("VICTIM-MIND-XYZ"));
    }

    @Test
    void rendererRejectsMissingOrUnusedVariables() {
        assertThrows(IllegalArgumentException.class, () -> PromptRenderer.render("{{a}}", Map.of()));
        var vars = new HashMap<String, String>(Map.of("a", "1", "b", "2"));
        assertThrows(IllegalArgumentException.class, () -> PromptRenderer.render("{{a}}", vars));
    }

    @Test
    void segmentsCoverAllCodePointsAndSilentAudioIsTimed() {
        String text = "妈，是我。我手机坏了！😀";
        var segments = SentenceSegmenter.split(text);
        assertEquals(4, segments.size()); // 妈， | 是我。 | 我手机坏了！ | 😀 (surrogate pair counted as one code point)
        assertEquals(0, segments.getFirst().charStart());
        assertEquals(text.codePointCount(0, text.length()), segments.getLast().charEnd());
        var audio = new SilentTtsClient().synthesize("v", 1.0, segments, Duration.ofSeconds(1));
        assertEquals((byte) 0xFF, audio.mp3()[0]);
        assertEquals(0, audio.mp3().length % 104);
        assertEquals(audio.totalMs(), audio.segments().getLast().endMs());
    }
}
