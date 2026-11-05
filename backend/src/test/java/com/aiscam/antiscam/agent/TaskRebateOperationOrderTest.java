package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskRebateOperationOrderTest {
    @Test
    void fourRoundsOfWaitingDoNotTurnAConditionalPayoutIntoAnOverduePayout() {
        var scene = new AgentScene(ScenarioCode.TASK_REBATE, "场景", "说明", "联系人", "本人", "default",
                "", "", "", "公开", true, "", true, false, true);
        var history = new ArrayList<DialogueLine>();
        for (int round = 9; round <= 12; round++) {
            history.add(new DialogueLine(round, SpeakerRole.SCAMMER,
                    "第二单38元本金加12元佣金共50元，操作确认后到账。", null, null));
            history.add(new DialogueLine(round, SpeakerRole.VICTIM,
                    "还没操作，也没看到50元到账，先不点。", null, null));
        }
        var current = new DialogueLine(13, SpeakerRole.SCAMMER,
                "第二单38元本金+12元佣金共50元，操作确认后立刻到账！现在开始？", null, null);
        var victim = AgentContextAssembler.victim(scene, history, current, 13, 60, false);
        assertTrue(victim.get("dialogue_history").contains("第9轮"));
        assertTrue(victim.get("dialogue_history").contains("第12轮"));
        String instructions = victim.get("scammer_line");
        assertTrue(instructions.startsWith(current.content()));
        assertTrue(instructions.contains("未到账只是条件未满足"));
        assertTrue(instructions.contains("可以理解规则但不接受先操作"));
        assertTrue(instructions.contains("已解释清楚且没有新变化的条件不重问"));
        assertTrue(instructions.contains("上一单应返未返与本单完成后才应返"));
        assertTrue(instructions.contains("不能把新加的付款、解冻或追加任务当作原有前提"));
        history.add(current);
        var narrator = AgentContextAssembler.narrator(scene, history, 13, PriorFacts.none());
        assertTrue(narrator.get("current_round").contains("不能仅因未到账判定违约"));
    }
}
