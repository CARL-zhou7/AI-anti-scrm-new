package com.aiscam.antiscam.simulation.domain;

public enum OutcomeCode {
    GOAL_ACHIEVED("诈骗目标达成"), LOSS_STOPPED("受骗后止损"), NOT_DEFRAUDED("未受骗结束"), UNDETERMINED("结局未定"),
    DEFRAUDED("已受骗"), IDENTIFIED("已识破"), LIMIT_REACHED("超限结束"),
    /** V1.4 15-round verdict (PRD-V3.5). */
    SCAMMER_WIN("骗子胜"), VICTIM_WIN("受害者胜");
    private final String displayText;
    OutcomeCode(String displayText) { this.displayText = displayText; }
    public boolean isV12() { return this == DEFRAUDED || this == IDENTIFIED || this == LIMIT_REACHED; }
    public boolean isV35() { return this == SCAMMER_WIN || this == VICTIM_WIN; }
    public String displayText() { return displayText; }
}
