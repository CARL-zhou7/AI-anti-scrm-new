package com.aiscam.antiscam.simulation.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentPromptRepository extends JpaRepository<AgentPrompt, Long> {
    @Query("select p from AgentPrompt p where p.active = 1")
    List<AgentPrompt> findActive();
}
