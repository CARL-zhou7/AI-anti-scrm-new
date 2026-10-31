package com.aiscam.antiscam.simulation.rules;

/** Trust is one contributor; behaviour and an explicit stop independently affect risk. */
public final class TrustRiskRules {
    private TrustRiskRules() {}
    public static int score(int trust, int behaviouralRisk, boolean actualFraud, boolean stopped) {
        int behaviour = stopped ? 0 : actualFraud ? 100 : behaviouralRisk;
        return (int) Math.round(trust * 0.6 + behaviour * 0.4);
    }
    public static String reason(boolean actualFraud, boolean stopped, String behaviourReason) {
        if (stopped) return "本轮已明确停止配合，降低继续受骗风险；综合当前信任评估，已经发生的付款和损失仍保留。";
        return "综合当前信任程度与本轮行为评估：" + behaviourReason
                + (actualFraud ? "已发生实际受骗，持续往来仍有追加风险。" : "尚未实际受骗，付款仍受行动门槛与资金约束。");
    }
}
