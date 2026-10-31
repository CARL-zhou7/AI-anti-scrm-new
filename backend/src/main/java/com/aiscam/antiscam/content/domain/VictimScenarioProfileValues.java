package com.aiscam.antiscam.content.domain;

import com.aiscam.antiscam.common.web.RequestContent;
import com.aiscam.antiscam.common.error.ApiException;

public record VictimScenarioProfileValues(String scenarioCode, String needs, String relevantExperience, String judgmentBasis, String verificationHabits, String actionBoundaries, String stopConditions, String recentEvents, String consultationsTransactions, String sharedExperiences, String contactBackground, String initialTrustMode, Integer initialTrustScore, java.math.BigDecimal positiveFactor, java.math.BigDecimal negativeFactor, Integer smallAmountCapYuan) {
    private static void validateFactor(String field, java.math.BigDecimal value) {
        if (value==null || value.compareTo(new java.math.BigDecimal("0.10"))<0
                || value.compareTo(new java.math.BigDecimal("3.00"))>0 || value.scale()>2)
            throw ApiException.field(field,"VALUE_INVALID","系数须在0.10至3.00之间且最多两位小数");
    }
    public void validate(String prefix) {
        if (initialTrustScore==null || initialTrustScore<0 || initialTrustScore>100)
            throw ApiException.field(prefix+".initialTrustScore","VALUE_INVALID","初始信任分须为0至100的整数");
        validateFactor(prefix+".positiveFactor",positiveFactor); validateFactor(prefix+".negativeFactor",negativeFactor);
        if (smallAmountCapYuan==null || smallAmountCapYuan<0)
            throw ApiException.field(prefix+".smallAmountCapYuan","VALUE_INVALID","小额上限须为非负整数");
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
