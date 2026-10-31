package com.aiscam.antiscam.content;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.domain.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VictimScenarioProfileRulesTest {
    static VictimScenarioProfileValues scenario(String code) {
        return new VictimScenarioProfileValues(code, "需求", "经历", "依据", "核实", "边界", "停止", "昨天", "咨询", "共同经历", "背景", "信任", 50, new java.math.BigDecimal("1.00"), new java.math.BigDecimal("1.00"), 200);
    }
    static VictimProfileValues profile(int available, int reserved, List<VictimScenarioProfileValues> scenarios) {
        return new VictimProfileValues("测试", 30, "女", "身份", "描述", "家庭", "收入", 1000, 2000, available, reserved, 0,
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
                new VictimScenarioProfileValues("IMPERSONATION_LOAN", " ", "经历", "依据", "核实", "边界", "停止", "昨天", "咨询", "经历", "背景", "信任", 50, new java.math.BigDecimal("1.00"), new java.math.BigDecimal("1.00"), 200))).validate());
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

    @Test
    void splitReservesAndTrustParametersAreValidatedAndApplied() {
        var all=List.of(scenario("TASK_REBATE"),scenario("FAKE_INVESTMENT"),scenario("IMPERSONATION_LOAN"));
        var v=profile(500,1500,all);
        var split=new VictimProfileValues(v.name(),v.age(),v.gender(),v.identity(),v.description(),v.familyStatus(),v.incomeDescription(),
                v.monthlyExpenseYuan(),v.initialSavingsYuan(),v.availableFundsYuan(),1000,500,v.reservedFundsNote(),v.generalDecisionTraits(),
                v.contextPlace(),v.currentState(),v.antiFraudAwareness(),v.riskTendency(),v.personalityTraits(),v.avatarKey(),v.speakingAvatarKey(),all);
        var p=new VictimProfile(split);
        assertEquals(1000,p.getGeneralReserveFundsYuan()); assertEquals(500,p.getEmergencyReserveFundsYuan()); assertEquals(1500,p.getReservedFundsYuan());
        var x=all.getFirst();
        for (var factor : List.of(new java.math.BigDecimal("0.09"),new java.math.BigDecimal("3.01"),new java.math.BigDecimal("1.001")))
            assertThrows(ApiException.class,()->new VictimScenarioProfileValues(x.scenarioCode(),x.needs(),x.relevantExperience(),x.judgmentBasis(),x.verificationHabits(),x.actionBoundaries(),x.stopConditions(),x.recentEvents(),x.consultationsTransactions(),x.sharedExperiences(),x.contactBackground(),x.initialTrustMode(),50,factor,java.math.BigDecimal.ONE,200).validate("scenarioProfiles[0]"));
        assertEquals(50,p.scenarioProfile(ScenarioCode.TASK_REBATE).getInitialTrustScore());
    }
}
