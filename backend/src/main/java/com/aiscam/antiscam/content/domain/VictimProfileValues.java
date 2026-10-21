package com.aiscam.antiscam.content.domain;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.common.web.RequestContent;
import java.util.*;

public record VictimProfileValues(String name, Integer age, String gender, String identity, String description, String familyStatus, String incomeDescription, int monthlyExpenseYuan, int initialSavingsYuan, int availableFundsYuan, int reservedFundsYuan, String reservedFundsNote, String generalDecisionTraits, String contextPlace, String currentState, Level antiFraudAwareness, Level riskTendency, String personalityTraits, String avatarKey, String speakingAvatarKey, List<VictimScenarioProfileValues> scenarioProfiles) {
    public void validate() {
        RequestContent.checkField("familyStatus", familyStatus, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField("incomeDescription", incomeDescription, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField("reservedFundsNote", reservedFundsNote, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField("generalDecisionTraits", generalDecisionTraits, RequestContent.MAX_TEXT_CHARS);
        if (monthlyExpenseYuan < 0) throw ApiException.field("monthlyExpenseYuan", "VALUE_INVALID", "金额不能为负数");
        if (initialSavingsYuan < 0) throw ApiException.field("initialSavingsYuan", "VALUE_INVALID", "金额不能为负数");
        if (availableFundsYuan < 0 || availableFundsYuan > initialSavingsYuan) throw ApiException.field("availableFundsYuan", "VALUE_INVALID", "可动用资金须在0与初始存款之间");
        if (reservedFundsYuan < 0 || reservedFundsYuan > initialSavingsYuan) throw ApiException.field("reservedFundsYuan", "VALUE_INVALID", "预留资金须在0与初始存款之间");
        validateScenarios(scenarioProfiles);
    }

    public static void validateScenarios(List<VictimScenarioProfileValues> profiles) {
        if (profiles == null || profiles.size() != 3) throw ApiException.field("scenarioProfiles", "VALUE_INVALID", "请完整填写刷单、投资、亲友借款三套场景资料");
        var codes = new HashSet<String>();
        for (int i = 0; i < profiles.size(); i++) {
            var profile = profiles.get(i);
            String prefix = "scenarioProfiles[" + i + "]";
            if (profile == null) throw ApiException.field(prefix, "REQUIRED", "请填写场景资料");
            profile.validate(prefix);
            if (!codes.add(profile.scenarioCode())) throw ApiException.field(prefix + ".scenarioCode", "VALUE_INVALID", "场景不能重复");
        }
    }
}
