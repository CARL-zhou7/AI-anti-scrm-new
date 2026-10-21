package com.aiscam.antiscam.content.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "victim_scenario_profile")
public class VictimScenarioProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "victim_profile_id", nullable = false)
    private VictimProfile victimProfile;
    @Column(name = "scenario_code", nullable = false, length = 32)
    private String scenarioCode;
    @Column(name = "needs", nullable = false, columnDefinition = "text")
    private String needs;
    @Column(name = "relevant_experience", nullable = false, columnDefinition = "text")
    private String relevantExperience;
    @Column(name = "judgment_basis", nullable = false, columnDefinition = "text")
    private String judgmentBasis;
    @Column(name = "verification_habits", nullable = false, columnDefinition = "text")
    private String verificationHabits;
    @Column(name = "action_boundaries", nullable = false, columnDefinition = "text")
    private String actionBoundaries;
    @Column(name = "stop_conditions", nullable = false, columnDefinition = "text")
    private String stopConditions;
    @Column(name = "recent_events", nullable = false, columnDefinition = "text")
    private String recentEvents;
    @Column(name = "consultations_transactions", nullable = false, columnDefinition = "text")
    private String consultationsTransactions;
    @Column(name = "shared_experiences", nullable = false, columnDefinition = "text")
    private String sharedExperiences;
    @Column(name = "contact_background", nullable = false, columnDefinition = "text")
    private String contactBackground;
    @Column(name = "initial_trust_mode", nullable = false, columnDefinition = "text")
    private String initialTrustMode;
    @Version @Column(name = "row_version", nullable = false)
    private int rowVersion;

    protected VictimScenarioProfile() {}
    public VictimScenarioProfile(VictimProfile victimProfile, VictimScenarioProfileValues values) {
        this.victimProfile = victimProfile;
        this.scenarioCode = values.scenarioCode();
        apply(values);
    }
    public String getScenarioCode() { return scenarioCode; }
    public void apply(VictimScenarioProfileValues values) {
        needs = values.needs();
        relevantExperience = values.relevantExperience();
        judgmentBasis = values.judgmentBasis();
        verificationHabits = values.verificationHabits();
        actionBoundaries = values.actionBoundaries();
        stopConditions = values.stopConditions();
        recentEvents = values.recentEvents();
        consultationsTransactions = values.consultationsTransactions();
        sharedExperiences = values.sharedExperiences();
        contactBackground = values.contactBackground();
        initialTrustMode = values.initialTrustMode();
    }
    public VictimScenarioProfileValues values() {
        return new VictimScenarioProfileValues(scenarioCode, needs, relevantExperience, judgmentBasis, verificationHabits, actionBoundaries, stopConditions, recentEvents, consultationsTransactions, sharedExperiences, contactBackground, initialTrustMode);
    }
}
