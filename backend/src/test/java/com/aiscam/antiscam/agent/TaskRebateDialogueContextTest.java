package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class TaskRebateDialogueContextTest {
    @ParameterizedTest
    @EnumSource(ScenarioCode.class)
    void taskTermsRemainVisibleWhenTheCurrentMessageOnlyUsesAShortQuote(ScenarioCode scenario) {
        var scene = new AgentScene(scenario, "场景", "说明", "联系人", "本人", "default",
                "", "", "", "公开", true, "", true, false, true);
        var history = List.of(
                new DialogueLine(13, SpeakerRole.SCAMMER, "体验单我垫付，你不用出钱。", null, "PRIVATE"),
                new DialogueLine(14, SpeakerRole.SCAMMER,
                        "新任务需要你垫付58元，完成返70元含本金，净赚12元，付给模拟商户。", null, "PRIVATE"));
        var current = new DialogueLine(15, SpeakerRole.SCAMMER, "现在接58元单返70元，最后3个名额。", null, "PRIVATE");
        var victim = AgentContextAssembler.victim(scene, history, current, 15, 60, false);
        assertTrue(victim.get("dialogue_history").contains("需要你垫付58元"));
        assertTrue(victim.get("dialogue_history").contains("净赚12元"));
        assertFalse(victim.toString().contains("PRIVATE"));
        assertTrue(victim.get("scammer_line").startsWith(current.content()));
        var scammer = AgentContextAssembler.scammer(scene, history, 15, 60);
        boolean rebate = scenario == ScenarioCode.TASK_REBATE;
        assertEquals(rebate, victim.get("scammer_line").contains("仍沿用该出资条件"));
        assertEquals(rebate, victim.get("scammer_line").contains("含本金的返还总额不是净收益"));
        assertEquals(rebate, scammer.get("pacing_hint").contains("明确谁先垫付多少元"));
        assertEquals(rebate, scammer.get("pacing_hint").contains("先澄清再推进"));
    }
}
