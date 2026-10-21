package com.aiscam.antiscam.content.web.dto;

import com.aiscam.antiscam.content.domain.*;
import java.util.List;

public record VictimView(String id, AvatarRef avatar, AvatarRef speakingAvatar, String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level antiFraudAwareness, Level riskTendency, String personalityTraits, String familyStatus, String incomeDescription, Integer monthlyExpenseYuan, Integer initialSavingsYuan, Integer availableFundsYuan, Integer reservedFundsYuan, String reservedFundsNote, String generalDecisionTraits, List<VictimScenarioProfileValues> scenarioProfiles, Integer profileSchemaVersion) {
    public static VictimView from(VictimProfile p) {
        return new VictimView(p.getId().toString(), AvatarRef.from(p.getAvatarKey()), AvatarRef.from(p.getSpeakingAvatarKey()), p.getName(), p.getAge(), p.getGender(), p.getIdentity(), p.getDescription(), p.getContextTime(), p.getContextPlace(), p.getCurrentState(), p.getAntiFraudAwareness(), p.getRiskTendency(), p.getPersonalityTraits(), p.getFamilyStatus(), p.getIncomeDescription(), p.getMonthlyExpenseYuan(), p.getInitialSavingsYuan(), p.getAvailableFundsYuan(), p.getReservedFundsYuan(), p.getReservedFundsNote(), p.getGeneralDecisionTraits(), p.getScenarioProfiles(), 2);
    }
}
