package com.aiscam.antiscam.content.domain;

import com.aiscam.antiscam.common.web.RequestContent;
import com.aiscam.antiscam.common.error.ApiException;

public record VictimScenarioProfileValues(String scenarioCode, String needs, String relevantExperience, String judgmentBasis, String verificationHabits, String actionBoundaries, String stopConditions, String recentEvents, String consultationsTransactions, String sharedExperiences, String contactBackground, String initialTrustMode) {
    public void validate(String prefix) {
        try { ScenarioCode.valueOf(scenarioCode); }
        catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.field(prefix + ".scenarioCode", "VALUE_INVALID", "请选择有效场景");
        }
        RequestContent.checkField(prefix + ".needs", needs, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".relevantExperience", relevantExperience, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".judgmentBasis", judgmentBasis, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".verificationHabits", verificationHabits, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".actionBoundaries", actionBoundaries, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".stopConditions", stopConditions, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".recentEvents", recentEvents, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".consultationsTransactions", consultationsTransactions, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".sharedExperiences", sharedExperiences, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".contactBackground", contactBackground, RequestContent.MAX_TEXT_CHARS);
        RequestContent.checkField(prefix + ".initialTrustMode", initialTrustMode, RequestContent.MAX_TEXT_CHARS);
    }
}
