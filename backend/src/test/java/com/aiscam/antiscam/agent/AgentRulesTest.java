package com.aiscam.antiscam.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.media.SentenceSegmenter;
import com.aiscam.antiscam.media.SilentTtsClient;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
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
                read("scammer"), read("victim"), read("narrator"));
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
