package com.aiscam.antiscam.simulation.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationMessageRepository extends JpaRepository<SimulationMessage, Long> {
    List<SimulationMessage> findByRoundIdIn(Collection<Long> roundIds);
    List<SimulationMessage> findByRoundId(long roundId);
    Optional<SimulationMessage> findByRoundIdAndSpeakerRole(long roundId, SpeakerRole role);
}
