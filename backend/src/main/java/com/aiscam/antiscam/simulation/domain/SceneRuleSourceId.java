package com.aiscam.antiscam.simulation.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link SceneRuleSource}; required by JPA for the (rule_id, intervention_id) primary key. */
public class SceneRuleSourceId implements Serializable {
    private Long ruleId;
    private Long interventionId;

    public SceneRuleSourceId() {}

    public SceneRuleSourceId(Long ruleId, Long interventionId) {
        this.ruleId = ruleId;
        this.interventionId = interventionId;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SceneRuleSourceId id)) return false;
        return Objects.equals(ruleId, id.ruleId) && Objects.equals(interventionId, id.interventionId);
    }

    @Override public int hashCode() { return Objects.hash(ruleId, interventionId); }
}
