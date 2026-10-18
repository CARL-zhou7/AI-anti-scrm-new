package com.aiscam.antiscam.simulation.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_prompt")
public class AgentPrompt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "prompt_type", nullable = false, length = 16)
    private PromptType promptType;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(name = "template_content", nullable = false, columnDefinition = "mediumtext")
    private String templateContent;
    @Column(name = "is_active", nullable = false)
    private Byte active;
    @Column(name = "active_type", insertable = false, updatable = false, length = 16)
    private String activeType;
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;

    protected AgentPrompt() {}
    public Long getId() { return id; }
    public PromptType getPromptType() { return promptType; }
    public int getVersionNo() { return versionNo; }
    public String getTemplateContent() { return templateContent; }
    public boolean isActive() { return active != null && active == 1; }
}
