package com.aiscam.antiscam.simulation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationTrustEventRepository extends JpaRepository<SimulationTrustEvent, Long> {
    java.util.List<SimulationTrustEvent> findBySessionIdOrderByIdAsc(long sessionId);
}
