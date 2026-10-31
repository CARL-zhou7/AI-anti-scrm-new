package com.aiscam.antiscam.simulation.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SimulationInterventionRepository extends JpaRepository<SimulationIntervention, Long> {
    Optional<SimulationIntervention> findByDraftId(long draftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SimulationIntervention i where i.draftId = :draftId")
    Optional<SimulationIntervention> lockByDraftId(@Param("draftId") long draftId);

    @Query("select i from SimulationIntervention i where i.adminUserId = :adminUserId and i.status = :status "
            + "order by i.id desc")
    List<SimulationIntervention> findForAdmin(@Param("adminUserId") long adminUserId,
            @Param("status") InterventionStatus status, Pageable pageable);

    @Query("select i from SimulationIntervention i where i.adminUserId = :adminUserId and i.status = :status "
            + "and i.sceneCode = :sceneCode order by i.id desc")
    List<SimulationIntervention> findForAdminByScene(@Param("adminUserId") long adminUserId,
            @Param("status") InterventionStatus status, @Param("sceneCode") String sceneCode, Pageable pageable);

    long countBySessionIdAndStatus(long sessionId, InterventionStatus status);

    List<SimulationIntervention> findByIdInAndAdminUserId(List<Long> ids, long adminUserId);

    /** Locks the selected sources of one induction so two administrators cannot merge the same record. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SimulationIntervention i where i.id in :ids and i.adminUserId = :adminUserId order by i.id")
    List<SimulationIntervention> lockByIdInAndAdminUserId(@Param("ids") List<Long> ids,
            @Param("adminUserId") long adminUserId);
}
