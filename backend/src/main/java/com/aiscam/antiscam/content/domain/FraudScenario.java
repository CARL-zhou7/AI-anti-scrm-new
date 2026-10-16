package com.aiscam.antiscam.content.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fraud_scenario")
public class FraudScenario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_code", nullable = false, length = 32)
    private ScenarioCode scenarioCode;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    @Version @Column(name = "row_version", nullable = false)
    private int rowVersion;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected FraudScenario() {}
    public Long getId() { return id; }
    public ScenarioCode getScenarioCode() { return scenarioCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
