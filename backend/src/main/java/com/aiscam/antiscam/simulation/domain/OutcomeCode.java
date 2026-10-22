package com.aiscam.antiscam.simulation.domain;

public enum OutcomeCode {
    GOAL_ACHIEVED("诈骗目标达成"), LOSS_STOPPED("受骗后止损"), NOT_DEFRAUDED("未受骗结束"), UNDETERMINED("结局未定"),
    DEFRAUDED("已受骗"), IDENTIFIED("已识破"), LIMIT_REACHED("超限结束");
    private final String displayText;
    OutcomeCode(String displayText) { this.displayText = displayText; }
    public boolean isV12() { return this == DEFRAUDED || this == IDENTIFIED || this == LIMIT_REACHED; }
    public String displayText() { return displayText; }
}
