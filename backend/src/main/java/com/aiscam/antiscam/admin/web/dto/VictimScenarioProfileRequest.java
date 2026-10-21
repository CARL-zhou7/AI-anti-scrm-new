package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import com.aiscam.antiscam.content.domain.VictimScenarioProfileValues;

public record VictimScenarioProfileRequest(@NotBlank String scenarioCode, @NotBlank String needs, @NotBlank String relevantExperience, @NotBlank String judgmentBasis, @NotBlank String verificationHabits, @NotBlank String actionBoundaries, @NotBlank String stopConditions, @NotBlank String recentEvents, @NotBlank String consultationsTransactions, @NotBlank String sharedExperiences, @NotBlank String contactBackground, @NotBlank String initialTrustMode) {
    public VictimScenarioProfileValues values() { return new VictimScenarioProfileValues(scenarioCode, needs, relevantExperience, judgmentBasis, verificationHabits, actionBoundaries, stopConditions, recentEvents, consultationsTransactions, sharedExperiences, contactBackground, initialTrustMode); }
}
