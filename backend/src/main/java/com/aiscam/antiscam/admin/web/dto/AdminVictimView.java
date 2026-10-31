package com.aiscam.antiscam.admin.web.dto;

import com.aiscam.antiscam.content.domain.*;
import java.util.List;
import com.aiscam.antiscam.content.web.dto.AvatarRef;

public record AdminVictimView(String id, AvatarRef avatar, AvatarRef speakingAvatar, String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level antiFraudAwareness, Level riskTendency, String personalityTraits, String familyStatus, String incomeDescription, Integer monthlyExpenseYuan, Integer initialSavingsYuan, Integer availableFundsYuan, Integer reservedFundsYuan, Integer generalReserveFundsYuan, Integer emergencyReserveFundsYuan, String reservedFundsNote, String generalDecisionTraits, List<VictimScenarioProfileValues> scenarioProfiles, Integer profileSchemaVersion, boolean enabled, int version, String createdAt, String updatedAt) {
    public static AdminVictimView from(VictimProfile p) {
        return new AdminVictimView(p.getId().toString(), AdminViews.avatar(p.getAvatarKey()), AdminViews.avatar(p.getSpeakingAvatarKey()), p.getName(), p.getAge(), p.getGender(), p.getIdentity(), p.getDescription(), p.getContextTime(), p.getContextPlace(), p.getCurrentState(), p.getAntiFraudAwareness(), p.getRiskTendency(), p.getPersonalityTraits(), p.getFamilyStatus(), p.getIncomeDescription(), p.getMonthlyExpenseYuan(), p.getInitialSavingsYuan(), p.getAvailableFundsYuan(), p.getReservedFundsYuan(), p.getGeneralReserveFundsYuan(), p.getEmergencyReserveFundsYuan(), p.getReservedFundsNote(), p.getGeneralDecisionTraits(), p.getScenarioProfiles(), 3, p.isEnabled(), p.getRowVersion(), AdminViews.time(p.getCreatedAt()), AdminViews.time(p.getUpdatedAt()));
    }
}
