package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.content.domain.ScenarioCode;

public final class FriendFeedbackRules {
    private FriendFeedbackRules() {}
    public enum ConsultTarget { CLAIMED_PERSON, THIRD_PARTY }
    public record Feedback(boolean decisive, String text) {}
    public static Feedback reply(ScenarioCode scenario, ConsultTarget target) {
        if (scenario == ScenarioCode.IMPERSONATION_LOAN) return target == ConsultTarget.CLAIMED_PERSON
                ? new Feedback(true,"我没跟你借过钱，也没换号，你别转。")
                : new Feedback(false,"我暂时联系不上他，你直接用原有号码联系本人确认。");
        return new Feedback(false,scenario == ScenarioCode.TASK_REBATE
                ? "我没做过这种，先别付钱，问清楚再说。" : "我不懂这个，建议你去银行问。");
    }
}
