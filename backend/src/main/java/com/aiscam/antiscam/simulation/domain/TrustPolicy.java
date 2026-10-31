package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "simulation_policy")
public class TrustPolicy {
    @Id
    @Column(name = "id", columnDefinition = "tinyint unsigned")
    private Integer id;
    @Column(name = "trust_threshold_accept", columnDefinition = "smallint unsigned")
    private int trustThresholdAccept;
    @Column(name = "trust_threshold_small", columnDefinition = "smallint unsigned")
    private int trustThresholdSmall;
    @Column(name = "trust_threshold_large", columnDefinition = "smallint unsigned")
    private int trustThresholdLarge;
    @Version
    @Column(name = "row_version", columnDefinition = "int unsigned")
    private int rowVersion;
    @Column(name = "updated_at", columnDefinition = "datetime(3)", insertable = false, updatable = false)
    private Instant updatedAt;

    protected TrustPolicy() {}
    public void update(int accept, int small, int large) {
        trustThresholdAccept=accept; trustThresholdSmall=small; trustThresholdLarge=large;
    }
    public Integer getId() { return id; }
    public int getTrustThresholdAccept() { return trustThresholdAccept; }
    public int getTrustThresholdSmall() { return trustThresholdSmall; }
    public int getTrustThresholdLarge() { return trustThresholdLarge; }
    public int getRowVersion() { return rowVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
}
