package com.aiscam.antiscam.simulation.domain;

public enum ReportErrorCode {
    GENERATION_FAILED("报告生成失败，可手动重试。"),
    VALIDATION_FAILED("报告内容未通过校验，可手动重试。"),
    PERSISTENCE_FAILED("报告保存失败，可手动重试。"),
    TIMEOUT("报告生成超时，可手动重试。"),
    SERVICE_RESTARTED("服务重启导致报告生成中断，可手动重试。");

    private final String message;
    ReportErrorCode(String message) { this.message = message; }
    public String message() { return message; }
}
