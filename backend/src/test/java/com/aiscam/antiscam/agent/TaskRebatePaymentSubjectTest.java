package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskRebatePaymentSubjectTest {
    @Test
    void victimAndNarratorKeepThePayerAttachedToTheSpeaker() {
        var scene = new AgentScene(ScenarioCode.TASK_REBATE, "场景", "说明", "联系人", "本人", "default",
                "", "", "", "公开", true, "", true, false, true);
        var offer = new DialogueLine(3, SpeakerRole.SCAMMER,
                "说明里写了3单全由我垫付，完成后18元直接返你微信，现在开始第一单？", null, "PRIVATE");
        var reply = new DialogueLine(3, SpeakerRole.VICTIM,
                "现在又说要我垫付，我有点不放心。", null, null);
        var victim = AgentContextAssembler.victim(scene, List.of(), offer, 3, 60, false);
        String instruction = victim.get("scammer_line");
        assertTrue(instruction.startsWith(offer.content()));
        assertTrue(instruction.contains("是联系人自己出钱"));
        assertTrue(instruction.contains("联系人说‘你垫付’才是要求你出钱"));
        assertTrue(instruction.contains("不构成你的付款请求"));
        assertFalse(victim.toString().contains("PRIVATE"));
        var narrator = AgentContextAssembler.narrator(scene, List.of(offer, reply), 3, PriorFacts.none());
        assertTrue(narrator.get("current_round").contains("应识别为受害者误解"));
        assertTrue(narrator.get("current_round").contains("承诺垫付不等于已经付款"));
    }
}
