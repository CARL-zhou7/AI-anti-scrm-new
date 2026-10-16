package com.aiscam.antiscam.content.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scammer_profile")
public class ScammerProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "name", nullable = false, length = 64)
    private String name;
    @Column(name = "avatar_key", nullable = false, length = 255)
    private String avatarKey;
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
    @Column(name = "fraud_experience", nullable = false, length = 8)
    private Level fraudExperience;
    @Enumerated(EnumType.STRING)
    @Column(name = "speech_skill", nullable = false, length = 8)
    private Level speechSkill;
    @Enumerated(EnumType.STRING)
    @Column(name = "manipulation_skill", nullable = false, length = 8)
    private Level manipulationSkill;
    @Column(name = "behavior_traits", nullable = false, columnDefinition = "text")
    private String behaviorTraits;
    @Column(name = "is_enabled", nullable = false)
    private Byte enabled;
    @Version @Column(name = "row_version", nullable = false)
    private int rowVersion;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant updatedAt;

    protected ScammerProfile() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAvatarKey() { return avatarKey; }
    public Integer getAge() { return age; }
    public String getGender() { return gender; }
    public String getIdentity() { return identity; }
    public String getDescription() { return description; }
    public String getContextTime() { return contextTime; }
    public String getContextPlace() { return contextPlace; }
    public String getCurrentState() { return currentState; }
    public Level getFraudExperience() { return fraudExperience; }
    public Level getSpeechSkill() { return speechSkill; }
    public Level getManipulationSkill() { return manipulationSkill; }
    public String getBehaviorTraits() { return behaviorTraits; }
    public Byte getEnabled() { return enabled; }
}
