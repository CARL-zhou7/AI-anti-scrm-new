package com.aiscam.antiscam.simulation.domain;

public enum ReportStatus {
    PENDING("生成中"), RUNNING("生成中"), SUCCEEDED("已完成"), FAILED("生成失败");
    private final String displayText;
    ReportStatus(String displayText) { this.displayText = displayText; }
    public String displayText() { return displayText; }
    public boolean canRetry() { return this == FAILED; }
}
