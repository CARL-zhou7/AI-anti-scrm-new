package com.aiscam.antiscam.simulation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitingItemRepository extends JpaRepository<WaitingItem, Long> {
    java.util.List<WaitingItem> findBySessionIdOrderByIdAsc(long sessionId);
    java.util.Optional<WaitingItem> findBySessionIdAndItemTypeAndSubjectKey(long sessionId, WaitingItemType type, String subjectKey);
}
