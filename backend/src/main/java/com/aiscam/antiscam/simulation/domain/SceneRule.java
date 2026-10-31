package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A scenario knowledge-base rule. Only enabled rules enter a new session's frozen snapshot. */
@Entity
@Table(name = "scene_rule")
public class SceneRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "bigint unsigned")
    private Long id;
    @Column(name = "scenario_code", nullable = false, length = 32)
    private String scenarioCode;
    @Column(name = "title", nullable = false, length = 128)
    private String title;
    @Column(name = "rule_text", nullable = false, columnDefinition = "text")
    private String ruleText;
    @Column(name = "version_no", nullable = false, columnDefinition = "int unsigned")
    private int versionNo;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16)
    private SceneRuleStatus status;
    @Column(name = "created_by", nullable = false, columnDefinition = "bigint unsigned")
    private Long createdBy;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected SceneRule() {}

    public SceneRule(String scenarioCode, String title, String ruleText, long createdBy) {
        this.scenarioCode = scenarioCode;
        this.title = title;
        this.ruleText = ruleText;
        this.createdBy = createdBy;
        this.versionNo = 1;
        this.status = SceneRuleStatus.ENABLED;
    }

    public Long getId() { return id; }
    public String getScenarioCode() { return scenarioCode; }
    public String getTitle() { return title; }
    public String getRuleText() { return ruleText; }
    public int getVersionNo() { return versionNo; }
    public SceneRuleStatus getStatus() { return status; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isEnabled() { return status == SceneRuleStatus.ENABLED; }

    /** Editing or disabling always bumps the version; existing snapshots keep the old text. */
    public void update(String title, String ruleText, SceneRuleStatus status) {
        if (title != null && !title.isBlank()) this.title = title;
        if (ruleText != null && !ruleText.isBlank()) this.ruleText = ruleText;
        if (status != null) this.status = status;
        this.versionNo++;
    }
}
