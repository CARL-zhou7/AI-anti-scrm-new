package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.AgentScene;
import com.aiscam.antiscam.agent.AgentModels.DialogueLine;
import com.aiscam.antiscam.agent.AgentModels.EvidenceSource;
import com.aiscam.antiscam.agent.AgentModels.PriorFacts;
import com.aiscam.antiscam.agent.AgentModels.TrustEventCandidate;
import com.aiscam.antiscam.agent.LlmClient.LlmRequest;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import com.aiscam.antiscam.simulation.rules.CounterevidenceLevel;
import com.aiscam.antiscam.simulation.rules.TrustEventType;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Version isolation between the legacy trust entries and trust-v3 (plan Task 2). New strict rules only apply to
 * trust-v3; the old parser/validator entry points keep their exact legacy semantics.
 */
class TrustV3CompatibilityTest {
    private static final String LEGACY_DECISIVE = """
            {"events":[{"type":"DECISIVE_COUNTEREVIDENCE","subjectKey":"身份","quote":"我根本不是你说的那个人","source":"CONTENT"}]}
            """;
    private static final String V3_EVENTS = """
            {"events":[
              {"type":"DECISIVE_COUNTEREVIDENCE","subjectKey":"material:日期矛盾","progressKey":"日期首次矛盾",
               "quote":"材料写的是10月25日","source":"CONTENT","speaker":"SCAMMER","eventOrder":1,
               "counterevidenceLevel":"PARTIAL"},
              {"type":"INDEPENDENT_SUPPORT","subjectKey":"态度:愿意了解","progressKey":"主动询问步骤",
               "quote":"那我先看看怎么操作","source":"CONTENT","speaker":"VICTIM","eventOrder":1}]}
            """;

    @Test
    void legacyEventEntryStillAcceptsDecisiveWithoutLevel() {
        var events = AgentOutputParser.event(LEGACY_DECISIVE);
        assertEquals(1, events.size());
        assertNull(events.getFirst().counterevidenceLevel());
        assertNull(events.getFirst().speaker());
    }

    @Test
    void v3EventsCarrySpeakerOrderAndLevel() {
        var events = AgentOutputParser.event(V3_EVENTS, true);
        assertEquals(2, events.size());
        assertEquals(CounterevidenceLevel.PARTIAL, events.getFirst().counterevidenceLevel());
        assertEquals(SpeakerRole.SCAMMER, events.getFirst().speaker());
        assertEquals(1, events.getFirst().eventOrder());
        assertEquals(SpeakerRole.VICTIM, events.get(1).speaker());
    }

    @Test
    void v3RejectsMissingLevelWrongLevelAndMissingSpeakerOrOrder() {
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"DECISIVE_COUNTEREVIDENCE","subjectKey":"身份","progressKey":"进展",
                  "quote":"我根本不是你说的那个人","source":"CONTENT","speaker":"SCAMMER","eventOrder":1}]}
                """, true), "decisive requires a level");
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"DECISIVE_COUNTEREVIDENCE","subjectKey":"身份","progressKey":"进展",
                  "quote":"我根本不是你说的那个人","source":"CONTENT","speaker":"SCAMMER","eventOrder":1,
                  "counterevidenceLevel":"DECISIVE"}]}
                """, true), "wrong level value");
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"NEW_CONTRADICTION","subjectKey":"日期","progressKey":"进展",
                  "quote":"我根本不是你说的那个人","source":"CONTENT","speaker":"SCAMMER","eventOrder":1,
                  "counterevidenceLevel":"PARTIAL"}]}
                """, true), "only decisive events may carry a level");
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"NEW_EFFECTIVE_EXPLANATION","subjectKey":"费用","progressKey":"进展",
                  "quote":"我根本不是你说的那个人","source":"CONTENT","eventOrder":1}]}
                """, true), "speaker is required in v3");
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"NEW_EFFECTIVE_EXPLANATION","subjectKey":"费用","progressKey":"进展",
                  "quote":"我根本不是你说的那个人","source":"CONTENT","speaker":"SCAMMER","eventOrder":0}]}
                """, true), "eventOrder must be positive");
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"NEW_EFFECTIVE_EXPLANATION","subjectKey":"费用",
                  "quote":"我根本不是你说的那个人","source":"CONTENT","speaker":"SCAMMER","eventOrder":1}]}
                """, true), "progressKey is required in v3");
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"INDEPENDENT_SUPPORT","subjectKey":"态度","progressKey":"进展",
                  "quote":"我根本不是你说的那个人","source":"CONTENT","speaker":"SCAMMER","eventOrder":1}]}
                """, true), "independent attitude must cite the victim");
    }

    @Test
    void legacyEntriesRejectResolutionAndNewFields() {
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event("""
                {"events":[{"type":"COUNTEREVIDENCE_RESOLVED","subjectKey":"身份","quote":"我根本不是你说的那个人","source":"CONTENT"}]}
                """), "legacy entry does not know the resolution type");
        assertThrows(AgentCallException.class, () -> AgentOutputParser.event(V3_EVENTS), "legacy entry rejects new fields");
        assertThrows(AgentCallException.class, () -> AgentOutputValidator.trustEvent(
                new TrustEventCandidate(TrustEventType.NEW_CONTRADICTION, "日期", "原文", EvidenceSource.CONTENT,
                        List.of(), null, "进展", CounterevidenceLevel.PARTIAL, SpeakerRole.SCAMMER, 1), false),
                "legacy validator rejects the level field");
    }

    @Test
    void v3NarratorDecisiveEvidenceIsNotBlockedByTheLegacyGuard() {
        String v3Narrator = """
                {"fraudStage":"试探期","speechAnalysis":"骗子先解释材料来源","methodAnalysis":"伪造材料",
                 "methodShort":"伪造材料","intentShort":"用一套自洽说法解释材料日期的疑点",
                 "victimPsychology":"开始怀疑","riskScore":50,"riskReason":"出现反证",
                 "actualFraudOccurred":false,"goalAchieved":false,"cooperationStopped":true,
                 "evidence":[
                   {"factCode":"DECISIVE_COUNTEREVIDENCE_FOUND","speaker":"VICTIM","source":"CONTENT","roundNo":1,
                    "quote":"材料日期和你说的不一样","eventOrder":1},
                   {"factCode":"COOPERATION_STOPPED","speaker":"VICTIM","source":"CONTENT","roundNo":1,
                    "quote":"你先别说了，我自己核实","eventOrder":2}],
                 "candidateOutcome":null,"candidateReason":null,"characterExit":false,"characterExitReason":null,
                 "attitudeEvents":[],"waitingUpdates":[]}
                """;
        var lines = List.of(
                new DialogueLine(1, SpeakerRole.SCAMMER, "材料写的是10月25日", null, "试探"),
                new DialogueLine(1, SpeakerRole.VICTIM, "材料日期和你说的不一样，你先别说了，我自己核实", null, "怀疑"));
        var v3Runner = new AgentRunner((LlmRequest request) -> v3Narrator, new SimulationProperties().getLlm());
        var v3 = v3Runner.narrator(scene(true), lines, 1, PriorFacts.none(), 5_000, 0, null, null, false, List.of());
        assertEquals("伪造材料", v3.judgement().methodShort());
        assertNull(v3.judgement().candidateOutcome());

        var legacyRunner = new AgentRunner((LlmRequest request) -> v3Narrator, new SimulationProperties().getLlm());
        assertThrows(AgentCallException.class,
                () -> legacyRunner.narrator(scene(false), lines, 1, PriorFacts.none(), 5_000, 0, null, null, false, List.of()),
                "a session without trust events still rejects the new evidence");
    }

    private static AgentScene scene(boolean trustV3) {
        return new AgentScene(ScenarioCode.TASK_REBATE, "刷单返利", "说明", "骗子", "受害者", "default",
                "scammer {{round_no}}", "victim {{round_no}}", NARRATOR_TEMPLATE, "公开背景", false, null, false, false,
                trustV3);
    }

    private static final String NARRATOR_TEMPLATE = """
            场景：{{scenario_name}}（{{scenario_code}}）
            目标：{{scenario_goal}}
            骗子：{{scammer_profile}}
            受害者：{{victim_profile}}
            历史：{{dialogue_history}}
            轮次：{{round_no}}
            本轮：{{current_round}}
            此前事实：{{previous_facts}}
            分析目标字数：{{analysis_target_chars}}
            """;
}
