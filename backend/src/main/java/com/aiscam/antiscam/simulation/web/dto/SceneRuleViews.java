package com.aiscam.antiscam.simulation.web.dto;

import com.aiscam.antiscam.simulation.domain.SceneRule;
import com.aiscam.antiscam.simulation.domain.SceneRuleStatus;
import java.util.List;

/** Knowledge-base DTOs (interface spec API-50～54). Rule text never carries names, balances or thresholds. */
public final class SceneRuleViews {
    private SceneRuleViews() {}

    /** Induction result: a proposal the administrator reviews before submitting. Nothing is written yet. */
    public record RuleDraft(String scenarioCode, String title, String ruleText, List<String> sourceInterventionIds) {}

    public record SceneRuleWrite(String scenarioCode, String title, String ruleText, List<String> sourceInterventionIds) {}

    public record SceneRuleUpdate(String title, String ruleText, String status, Integer version) {}

    public record SceneRuleView(String ruleId, String scenarioCode, String title, String ruleText, int version,
            String status, long sourceCount, String updatedAt) {
        public static SceneRuleView from(SceneRule rule, long sourceCount) {
            return new SceneRuleView(rule.getId().toString(), rule.getScenarioCode(), rule.getTitle(), rule.getRuleText(),
                    rule.getVersionNo(), rule.getStatus().name(), sourceCount,
                    com.aiscam.antiscam.common.time.UtcTime.format(rule.getUpdatedAt()));
        }
    }

    public record SceneRulePage(List<SceneRuleView> items) {}

    public static SceneRuleStatus status(String value) {
        try { return SceneRuleStatus.valueOf(value); }
        catch (IllegalArgumentException | NullPointerException e) {
            throw com.aiscam.antiscam.common.error.ApiException.field("status", "VALUE_INVALID", "状态只允许ENABLED或DISABLED");
        }
    }
}
