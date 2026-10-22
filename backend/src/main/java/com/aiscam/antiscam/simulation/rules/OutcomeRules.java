package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.simulation.domain.LimitReason;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;

/** Candidate outcome priority (PRD §10.3 BR-P04): goal reached → loss stopped → not defrauded → none. */
public final class OutcomeRules {
    public static final int MIN_ROUNDS = 5;
    public static final int MAX_ROUNDS = 20;
    private OutcomeRules() {}

    public static OutcomeCode expectedLegacy(boolean goalAchieved, boolean actualFraud, boolean stopped) {
        if (goalAchieved) return OutcomeCode.GOAL_ACHIEVED;
        if (actualFraud && stopped) return OutcomeCode.LOSS_STOPPED;
        if (!actualFraud && stopped) return OutcomeCode.NOT_DEFRAUDED;
        return null;
    }

    /**
     * Formal finish decision at a complete-round position. Returns null to keep running.
     * An explicit candidate always wins over limits; otherwise round 20 or an expired deadline yields UNDETERMINED.
     */
    public static Decision decide(int roundNo, OutcomeCode candidate, String candidateReason, boolean timeExpired) {
        if (candidate != null) return new Decision(candidate, candidateReason, null);
        if (roundNo >= MAX_ROUNDS) {
            LimitReason limit = timeExpired ? LimitReason.BOTH : LimitReason.ROUND_LIMIT;
            return new Decision(OutcomeCode.UNDETERMINED, "已完成第20轮仍未形成明确业务结局，达到轮数上限", limit);
        }
        if (timeExpired) {
            return new Decision(OutcomeCode.UNDETERMINED, "第" + roundNo + "轮完整结束时达到600秒有效时长上限，仍未形成明确业务结局",
                    LimitReason.TIME_LIMIT);
        }
        return null;
    }

    public static OutcomeCode expected(boolean goalAchieved, boolean actualFraud, boolean stopped) {
        if (goalAchieved || (actualFraud && stopped)) return OutcomeCode.DEFRAUDED;
        return !actualFraud && stopped ? OutcomeCode.IDENTIFIED : null;
    }

    public static Decision decide(int roundNo, OutcomeCode candidate, String reason, boolean timeExpired, boolean actualFraud) {
        if (roundNo >= MIN_ROUNDS && candidate != null) return new Decision(candidate, reason, null);
        LimitReason limit = roundNo >= MAX_ROUNDS ? (timeExpired ? LimitReason.BOTH : LimitReason.ROUND_LIMIT)
                : timeExpired ? LimitReason.TIME_LIMIT : null;
        if (limit == null) return null;
        return new Decision(actualFraud ? OutcomeCode.DEFRAUDED : OutcomeCode.LIMIT_REACHED,
                actualFraud ? "已发生实际受骗行为，达到演绎上限" : "达到演绎上限，尚未受骗或识破", limit);
    }

    public record Decision(OutcomeCode outcome, String reason, LimitReason limitReason) {}
}
