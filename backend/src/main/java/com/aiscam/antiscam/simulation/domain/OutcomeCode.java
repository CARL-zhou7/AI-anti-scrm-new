package com.aiscam.antiscam.simulation.domain;

public enum OutcomeCode {
    GOAL_ACHIEVED("诈骗目标达成"), LOSS_STOPPED("受骗后止损"), NOT_DEFRAUDED("未受骗结束"), UNDETERMINED("结局未定");
    private final String displayText;
    OutcomeCode(String displayText) { this.displayText = displayText; }
    public String displayText() { return displayText; }
}
