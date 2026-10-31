package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.agent.AgentModels.EvidenceItem;
import com.aiscam.antiscam.agent.AgentModels.NarratorJudgement;
import com.aiscam.antiscam.agent.AgentModels.PriorFacts;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.SimulationRound;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.rules.FactCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V1.4 keeps the complete analyses for reports/history and adds two short narration fields for the real-time card.
 * Both groups are saved in the same round and neither overwrites the other.
 */
class NarrationFieldsTest {
    private static final String LEGACY_NARRATOR = """
            {"fraudStage":"建立信任","speechAnalysis":"完整话术分析","methodAnalysis":"完整手段分析",
             "victimPsychology":"心理分析","riskScore":50,"riskReason":"试探",
             "actualFraudOccurred":false,"goalAchieved":false,"cooperationStopped":false,"evidence":[],
             "candidateOutcome":null,"candidateReason":null}
            """;
    private static final String V3_NARRATOR = """
            {"fraudStage":"建立信任","speechAnalysis":"完整话术分析","methodAnalysis":"完整手段分析",
             "methodShort":"冒充群助理","intentShort":"借群身份，让对方放下戒心并相信后续安排",
             "victimPsychology":"心理分析","riskScore":50,"riskReason":"试探",
             "actualFraudOccurred":false,"goalAchieved":false,"cooperationStopped":false,"evidence":[],
             "candidateOutcome":null,"candidateReason":null}
            """;
    private static final List<DialogueLine> LINES = List.of(
            new DialogueLine(1, SpeakerRole.SCAMMER, "我是群助理", null, "建立身份"),
            new DialogueLine(1, SpeakerRole.VICTIM, "好的，你说", null, "配合"));

    @Test
    void legacyNarrationWithoutShortFieldsParsesAsNullAndKeepsTheFullText() {
        var judgement = AgentOutputParser.narrator(LEGACY_NARRATOR);
        assertNull(judgement.methodShort());
        assertNull(judgement.intentShort());
        assertEquals("完整话术分析", judgement.speechAnalysis());
        assertEquals("完整手段分析", judgement.methodAnalysis());
    }

    @Test
    void invalidDisplayCopyFallsBackWithoutChangingCompleteAnalysesOrRisk() {
        for (String intent : new String[]{null,"","太短的意图","字".repeat(41),"字".repeat(50),"字".repeat(52)}) {
            var original = withShorts("冒充群助理",intent);
            var result = AgentOutputValidator.validate(original,ScenarioCode.TASK_REBATE,1,LINES,
                    PriorFacts.none(),null,null,true).judgement();
            assertNull(result.intentShort());
            assertEquals(original.methodShort(),result.methodShort());
            assertEquals(original.speechAnalysis(),result.speechAnalysis());
            assertEquals(original.methodAnalysis(),result.methodAnalysis());
            assertEquals(original.riskScore(),result.riskScore());
            assertEquals(original.evidence(),result.evidence());
        }
        for (String method : new String[]{null,"","字","字".repeat(13)}) {
            var result = AgentOutputValidator.validate(withShorts(method,"借群身份，让对方放下戒心并相信后续安排"),
                    ScenarioCode.TASK_REBATE,1,LINES,PriorFacts.none(),null,null,true).judgement();
            assertNull(result.methodShort());
            assertEquals("借群身份，让对方放下戒心并相信后续安排",result.intentShort());
        }
        assertDoesNotThrow(() -> AgentOutputValidator.validate(AgentOutputParser.narrator(LEGACY_NARRATOR),
                ScenarioCode.TASK_REBATE,1,LINES,PriorFacts.none(),null,null,true));
    }

    @Test void shortFieldBoundsCountUnicodeCodePointsIncludingPunctuation() {
        for (int length : List.of(12,40)) {
            String intent = "😀".repeat(length-1)+"。";
            var result = AgentOutputValidator.validate(withShorts("伪造收益",intent),ScenarioCode.TASK_REBATE,1,
                    LINES,PriorFacts.none(),null,null,true).judgement();
            assertEquals(intent,result.intentShort());
        }
        for (int length : List.of(11,41,46)) {
            var result = AgentOutputValidator.validate(withShorts("伪造收益","字".repeat(length)),ScenarioCode.TASK_REBATE,1,
                    LINES,PriorFacts.none(),null,null,true).judgement();
            assertNull(result.intentShort());
        }
    }

    @Test void oversizedCopyDoesNotHideInvalidRiskEvidenceOrOutcome() {
        String raw = V3_NARRATOR.replace("借群身份，让对方放下戒心并相信后续安排","字".repeat(52));
        for (String invalid : List.of(raw.replace("\"riskScore\":50","\"riskScore\":101"),
                raw.replace("\"candidateOutcome\":null","\"candidateOutcome\":\"IDENTIFIED\""),
                raw.replace("\"evidence\":[]","\"evidence\":[{\"factCode\":\"COOPERATION_STOPPED\",\"speaker\":\"VICTIM\",\"source\":\"CONTENT\",\"roundNo\":1,\"quote\":\"不存在的证据\",\"eventOrder\":1}]"))) {
            assertThrows(AgentCallException.class,() -> AgentOutputValidator.validate(AgentOutputParser.narrator(invalid),
                    ScenarioCode.TASK_REBATE,1,LINES,PriorFacts.none(),null,null,true));
        }
    }

    @Test void runnerAcceptsOversizedDisplayCopyInOneCallAndPersistsFallback() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var runner = new AgentRunner(request -> {
            calls.incrementAndGet();
            return V3_NARRATOR.replace("借群身份，让对方放下戒心并相信后续安排","字".repeat(52));
        },new com.aiscam.antiscam.config.SimulationProperties.Llm());
        var scene = new AgentModels.AgentScene(ScenarioCode.TASK_REBATE,"场景","简介","骗子","受害者","test",
                "","","{{scenario_name}} {{scenario_code}} {{scenario_goal}} {{scammer_profile}} {{victim_profile}} {{dialogue_history}} {{round_no}} {{current_round}} {{previous_facts}} {{analysis_target_chars}}",
                "公开画像",false,"",false,false,true);
        var result = runner.narrator(scene,LINES,1,PriorFacts.none(),10000,0,null,null,false,List.of()).judgement();
        assertEquals(1,calls.get());
        assertNull(result.intentShort());
        var round = new SimulationRound(1,1,Instant.EPOCH);
        round.saveAnalysis(result.fraudStage(),result.speechAnalysis(),result.methodAnalysis(),result.methodShort(),result.intentShort(),
                result.victimPsychology(),result.riskScore(),result.riskReason(),result.actualFraudOccurred(),result.goalAchieved(),
                result.cooperationStopped(),"[]",result.candidateOutcome(),result.candidateReason(),Instant.EPOCH);
        assertNull(round.getIntentShort());
        assertEquals("完整话术分析",round.getSpeechAnalysis());
    }

    @Test
    void shortFieldsAreSavedNextToTheCompleteAnalyses() {
        var round = new SimulationRound(1, 1, Instant.parse("2026-10-28T00:00:00Z"));
        round.saveAnalysis("建立信任", "完整话术分析", "完整手段分析", "冒充群助理", "借群身份，让对方放下戒心并相信后续安排",
                "心理分析", 50, "试探", false, false, false, "[]", null, null,
                Instant.parse("2026-10-28T00:00:05Z"));
        assertEquals("冒充群助理", round.getMethodShort());
        assertEquals("借群身份，让对方放下戒心并相信后续安排", round.getIntentShort());
        assertEquals("完整话术分析", round.getSpeechAnalysis());
        assertEquals("完整手段分析", round.getMethodAnalysis());

        // The legacy signature still works and leaves the short fields empty for old sessions.
        var legacy = new SimulationRound(1, 2, Instant.parse("2026-10-28T00:00:00Z"));
        legacy.saveAnalysis("建立信任", "旧话术分析", "旧手段分析", "心理", 50, "试探", false, false, false, "[]",
                null, null, Instant.parse("2026-10-28T00:00:05Z"));
        assertNull(legacy.getMethodShort());
        assertEquals("旧话术分析", legacy.getSpeechAnalysis());
    }

    private static NarratorJudgement withShorts(String methodShort, String intentShort) {
        return new NarratorJudgement("建立信任", "完整话术分析", "完整手段分析", "心理分析", 50, "试探", false, false, false,
                List.<EvidenceItem>of(), null, null, false, null, List.of(), List.of(), methodShort, intentShort);
    }

    @Test
    void v3NarrationStillRejectsAnInventedOutcome() {
        var invented = new NarratorJudgement("建立信任", "完整话术分析", "完整手段分析", "心理分析", 50, "试探", false, false,
                false, List.<EvidenceItem>of(), com.aiscam.antiscam.simulation.domain.OutcomeCode.IDENTIFIED, "自判识破",
                false, null, List.of(), List.of(), "冒充群助理", "借群身份，让对方放下戒心并相信后续安排");
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.validate(invented, ScenarioCode.TASK_REBATE, 1,
                LINES, PriorFacts.none(), null, null, true), "the program decides the outcome, not the model");
    }
}
