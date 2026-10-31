package com.aiscam.antiscam.admin.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import com.aiscam.antiscam.content.domain.VictimScenarioProfileValues;

public record VictimScenarioProfileRequest(@NotBlank String scenarioCode, @NotBlank String needs, @NotBlank String relevantExperience, @NotBlank String judgmentBasis, @NotBlank String verificationHabits, @NotBlank String actionBoundaries, @NotBlank String stopConditions, @NotBlank String recentEvents, @NotBlank String consultationsTransactions, @NotBlank String sharedExperiences, @NotBlank String contactBackground, @NotBlank String initialTrustMode,
        @NotNull @Min(0) @Max(100) Integer initialTrustScore,
        @NotNull @DecimalMin("0.10") @DecimalMax("3.00") @Digits(integer=1,fraction=2) BigDecimal positiveFactor,
        @NotNull @DecimalMin("0.10") @DecimalMax("3.00") @Digits(integer=1,fraction=2) BigDecimal negativeFactor,
        @NotNull @Min(0) Integer smallAmountCapYuan) {
    public VictimScenarioProfileValues values() { return new VictimScenarioProfileValues(scenarioCode, needs, relevantExperience, judgmentBasis, verificationHabits, actionBoundaries, stopConditions, recentEvents, consultationsTransactions, sharedExperiences, contactBackground, initialTrustMode, initialTrustScore, positiveFactor, negativeFactor, smallAmountCapYuan); }
}
