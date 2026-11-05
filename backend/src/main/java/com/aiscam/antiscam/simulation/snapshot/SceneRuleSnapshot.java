package com.aiscam.antiscam.simulation.snapshot;

/**
 * One frozen knowledge-base rule as it entered a session's snapshot (V1.4). Later edits or disabling never change a
 * recorded run, and the snapshot keeps the order the rules were injected in.
 */
public record SceneRuleSnapshot(String ruleId, String title, String ruleText, int version, String targetRole) {
    public SceneRuleSnapshot(String ruleId, String title, String ruleText, int version) {
        this(ruleId, title, ruleText, version, "SCAMMER");
    }
}
