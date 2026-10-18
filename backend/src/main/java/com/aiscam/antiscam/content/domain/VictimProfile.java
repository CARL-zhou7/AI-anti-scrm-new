package com.aiscam.antiscam.content.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "victim_profile")
public class VictimProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "name", nullable = false, length = 64)
    private String name;
    @Column(name = "avatar_key", nullable = false, length = 255)
    private String avatarKey;
    @Column(name = "speaking_avatar_key", nullable = false, length = 255)
    private String speakingAvatarKey;
    @Column(name = "age", nullable = false, columnDefinition = "smallint unsigned")
    private Integer age;
    @Column(name = "gender", nullable = false, length = 16)
    private String gender;
    @Column(name = "identity", nullable = false, length = 128)
    private String identity;
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "context_time", nullable = false, length = 128)
    private String contextTime;
    @Column(name = "context_place", nullable = false, length = 128)
    private String contextPlace;
    @Column(name = "current_state", nullable = false, columnDefinition = "text")
    private String currentState;
    @Enumerated(EnumType.STRING)
    @Column(name = "anti_fraud_awareness", nullable = false, length = 8)
    private Level antiFraudAwareness;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tendency", nullable = false, length = 8)
    private Level riskTendency;
    @Column(name = "personality_traits", nullable = false, columnDefinition = "text")
    private String personalityTraits;
    @Column(name = "is_enabled", nullable = false)
    private Byte enabled;
    @Version @Column(name = "row_version", nullable = false)
    private int rowVersion;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected VictimProfile() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAvatarKey() { return avatarKey; }
    public String getSpeakingAvatarKey() { return speakingAvatarKey; }
    public Integer getAge() { return age; }
    public String getGender() { return gender; }
    public String getIdentity() { return identity; }
    public String getDescription() { return description; }
    public String getContextTime() { return contextTime; }
    public String getContextPlace() { return contextPlace; }
    public String getCurrentState() { return currentState; }
    public Level getAntiFraudAwareness() { return antiFraudAwareness; }
    public Level getRiskTendency() { return riskTendency; }
    public String getPersonalityTraits() { return personalityTraits; }
    public Byte getEnabled() { return enabled; }

    public VictimProfile(VictimProfileValues values) {
        apply(values);
        enabled = 1;
    }

    public void apply(VictimProfileValues values) {
        name = values.name();
        age = values.age();
        gender = values.gender();
        identity = values.identity();
        description = values.description();
        contextTime = values.contextTime();
        contextPlace = values.contextPlace();
        currentState = values.currentState();
        antiFraudAwareness = values.antiFraudAwareness();
        riskTendency = values.riskTendency();
        personalityTraits = values.personalityTraits();
        avatarKey = values.avatarKey();
        speakingAvatarKey = values.speakingAvatarKey();
    }
    public void setEnabled(boolean value) { enabled = (byte) (value ? 1 : 0); }
    public boolean isEnabled() { return enabled == 1; }
    public int getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
