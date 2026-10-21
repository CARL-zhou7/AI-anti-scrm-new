package com.aiscam.antiscam.content.domain;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface VictimScenarioProfileRepository extends Repository<VictimScenarioProfile, Long> {
    List<VictimScenarioProfile> findByVictimProfileIdOrderByScenarioCodeAsc(long id);
}
