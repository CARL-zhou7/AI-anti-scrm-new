package com.aiscam.antiscam.simulation.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRoundRepository extends JpaRepository<SimulationRound, Long> {
    Optional<SimulationRound> findBySessionIdAndRoundNo(long sessionId, int roundNo);
    List<SimulationRound> findBySessionIdOrderByRoundNoAsc(long sessionId);
}
