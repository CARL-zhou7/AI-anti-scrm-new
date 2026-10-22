package com.aiscam.antiscam.simulation.domain;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface FundFlowRepository extends Repository<FundFlow, Long> {
    List<FundFlow> findBySessionIdOrderBySequenceNoAsc(long sessionId);
    FundFlow save(FundFlow flow);
}
