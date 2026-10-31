package com.aiscam.antiscam.content.web.dto;

import com.aiscam.antiscam.content.domain.VictimScenarioProfileValues;

public record VictimScenarioProfileView(String scenarioCode, String needs, String relevantExperience, String judgmentBasis, String verificationHabits, String actionBoundaries, String stopConditions, String recentEvents, String consultationsTransactions, String sharedExperiences, String contactBackground, String initialTrustMode) {
    public static VictimScenarioProfileView from(VictimScenarioProfileValues v) {
        return new VictimScenarioProfileView(v.scenarioCode(),v.needs(),v.relevantExperience(),v.judgmentBasis(),
                v.verificationHabits(),v.actionBoundaries(),v.stopConditions(),v.recentEvents(),v.consultationsTransactions(),
                v.sharedExperiences(),v.contactBackground(),v.initialTrustMode());
    }
}
