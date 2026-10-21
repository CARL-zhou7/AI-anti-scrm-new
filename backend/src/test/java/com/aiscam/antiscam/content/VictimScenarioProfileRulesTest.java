package com.aiscam.antiscam.content;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VictimScenarioProfileRulesTest {
    static VictimScenarioProfileValues scenario(String code) {
        return new VictimScenarioProfileValues(code, "需求", "经历", "依据", "核实", "边界", "停止", "昨天", "咨询", "共同经历", "背景", "信任");
    }
    static VictimProfileValues profile(int available, int reserved, List<VictimScenarioProfileValues> scenarios) {
        return new VictimProfileValues("测试", 30, "女", "身份", "描述", "家庭", "收入", 1000, 2000, available, reserved,
                "预留安排", "决策特点", "房间", "查看消息", Level.MEDIUM, Level.MEDIUM, "性格", "avatars/a.png", "avatars/b.png", scenarios);
    }
    @Test
    void requiresExactlyThreeDistinctKnownScenariosAndValidAmounts() {
        var all = List.of(scenario("TASK_REBATE"), scenario("FAKE_INVESTMENT"), scenario("IMPERSONATION_LOAN"));
        assertDoesNotThrow(() -> profile(1000, 1000, all).validate());
        for (var invalid : List.of(all.subList(0, 2), List.of(all.get(0), all.get(0), all.get(2)),
                List.of(all.get(0), all.get(1), scenario("UNKNOWN")))) {
            assertEquals(400, assertThrows(ApiException.class, () -> profile(1000, 1000, invalid).validate()).status);
        }
        for (var amounts : List.of(new int[]{-1, 0}, new int[]{0, -1}, new int[]{2001, 0}, new int[]{0, 2001})) {
            assertThrows(ApiException.class, () -> profile(amounts[0], amounts[1], all).validate());
        }
        assertThrows(ApiException.class, () -> profile(0, 0, null).validate());
        assertThrows(ApiException.class, () -> profile(0, 0, List.of(scenario("TASK_REBATE"), scenario("FAKE_INVESTMENT"),
                new VictimScenarioProfileValues("IMPERSONATION_LOAN", " ", "经历", "依据", "核实", "边界", "停止", "昨天", "咨询", "经历", "背景", "信任"))).validate());
    }

    @Test
    void applyingProfileReplacesThreeScenariosAndFixesStoryTime() {
        var all = List.of(scenario("TASK_REBATE"), scenario("FAKE_INVESTMENT"), scenario("IMPERSONATION_LOAN"));
        var p = new VictimProfile(profile(1000, 1000, all));
        p.apply(profile(500, 1500, all));
        assertEquals("昨天20:00", p.getContextTime());
        assertEquals(500, p.getAvailableFundsYuan());
        assertEquals(3, p.getScenarioProfiles().size());
    }
}
