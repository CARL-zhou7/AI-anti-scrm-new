package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Traceability row: which adopted intervention contributed to which rule. A source belongs to at most one rule. */
@Entity
@Table(name = "scene_rule_source")
@IdClass(SceneRuleSourceId.class)
public class SceneRuleSource {
    @Id @Column(name = "rule_id", columnDefinition = "bigint unsigned")
    private Long ruleId;
    @Id @Column(name = "intervention_id", columnDefinition = "bigint unsigned")
    private Long interventionId;
    @Column(name = "admin_user_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long adminUserId;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;

    protected SceneRuleSource() {}

    public SceneRuleSource(long ruleId, long interventionId, long adminUserId) {
        this.ruleId = ruleId;
        this.interventionId = interventionId;
        this.adminUserId = adminUserId;
    }

    public Long getRuleId() { return ruleId; }
    public Long getInterventionId() { return interventionId; }
    public Long getAdminUserId() { return adminUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
