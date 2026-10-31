package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.simulation.domain.LimitReason;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;

/** Candidate outcome priority (PRD §10.3 BR-P04): goal reached → loss stopped → not defrauded → none. */
public final class OutcomeRules {
    public static final int MIN_ROUNDS = 5;
    public static final int MAX_ROUNDS = 20;
    /** V1.4: exactly 15 rounds, then a two-way verdict (PRD-V3.5). */
    public static final int MAX_ROUNDS_V35 = 15;
    public static final int V35_WIN_SCORE = 80;
    private OutcomeRules() {}

    /**
     * Final verdict after round 15 only. High final trust or any net loss means the scammer won; otherwise the
     * victim won. The program never ends early and never sets a limit reason for a normal verdict.
     */
    public static Decision decideV35(int roundNo, int finalScore, int actualLossYuan) {
        if (roundNo < MAX_ROUNDS_V35) return null;
        if (finalScore >= V35_WIN_SCORE || actualLossYuan > 0) {
            return new Decision(OutcomeCode.SCAMMER_WIN, "第15轮结束：最终受骗倾向分" + finalScore + "，实际净损失"
                    + actualLossYuan + "元，判定骗子胜", null);
        }
        return new Decision(OutcomeCode.VICTIM_WIN, "第15轮结束：最终受骗倾向分" + finalScore + "，实际净损失"
                + actualLossYuan + "元，判定受害者胜", null);
    }

    public static String displayV2(OutcomeCode code, boolean goal, boolean stoppedAfterFraud) {
        if (code == OutcomeCode.DEFRAUDED && goal) return "大额投入目标达成";
        if (code == OutcomeCode.DEFRAUDED && stoppedAfterFraud) return "已受骗（小额投入，已止损）";
        if (code == OutcomeCode.DEFRAUDED) return "已受骗（小额投入）";
        return code == OutcomeCode.LIMIT_REACHED ? "演绎结果未定" : code.displayText();
    }

    public static OutcomeCode expectedV2(boolean goalAchieved, boolean actualFraud, boolean stopped, boolean decisiveEvidence) {
        if (goalAchieved || actualFraud && stopped) return OutcomeCode.DEFRAUDED;
        return stopped ? (decisiveEvidence ? OutcomeCode.IDENTIFIED : OutcomeCode.NOT_DEFRAUDED) : null;
    }

    public static Decision decideV2(int roundNo, OutcomeCode candidate, String reason, boolean timeExpired, boolean actualFraud) {
        if (candidate != null) return new Decision(candidate, reason, null);
        LimitReason limit = roundNo >= MAX_ROUNDS ? (timeExpired ? LimitReason.BOTH : LimitReason.ROUND_LIMIT)
                : timeExpired ? LimitReason.TIME_LIMIT : null;
        return limit == null ? null : new Decision(actualFraud ? OutcomeCode.DEFRAUDED : OutcomeCode.LIMIT_REACHED,
                actualFraud ? "已受骗（小额投入），尚未形成大额投入，达到演绎上限；已发生的付款和损失保留"
                        : "达到演绎上限，尚未付款或形成明确识破结局", limit);
    }

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
        return decide(roundNo, candidate, reason, timeExpired, actualFraud, false);
    }

    public static Decision decide(int roundNo, OutcomeCode candidate, String reason, boolean timeExpired,
            boolean actualFraud, boolean earlyException) {
        if (earlyException && roundNo < MIN_ROUNDS && candidate != null) {
            return new Decision(actualFraud ? OutcomeCode.DEFRAUDED : OutcomeCode.IDENTIFIED, reason, null);
        }
        if (roundNo >= MIN_ROUNDS && candidate != null) return new Decision(candidate, reason, null);
        LimitReason limit = roundNo >= MAX_ROUNDS ? (timeExpired ? LimitReason.BOTH : LimitReason.ROUND_LIMIT)
                : timeExpired ? LimitReason.TIME_LIMIT : null;
        if (limit == null) return null;
        return new Decision(actualFraud ? OutcomeCode.DEFRAUDED : OutcomeCode.LIMIT_REACHED,
                actualFraud ? "已发生实际受骗行为，达到演绎上限" : "达到演绎上限，尚未受骗或识破", limit);
    }

    public record Decision(OutcomeCode outcome, String reason, LimitReason limitReason) {}
}
