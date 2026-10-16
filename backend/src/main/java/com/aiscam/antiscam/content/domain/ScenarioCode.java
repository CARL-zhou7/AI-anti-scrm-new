package com.aiscam.antiscam.content.domain;

public enum ScenarioCode {
    TASK_REBATE("青年群体"), FAKE_INVESTMENT("中年群体"), IMPERSONATION_LOAN("老年群体");
    private final String focus;
    ScenarioCode(String focus) { this.focus = focus; }
    public String focus() { return focus; }
}
