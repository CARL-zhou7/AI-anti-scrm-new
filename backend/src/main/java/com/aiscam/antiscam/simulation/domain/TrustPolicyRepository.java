package com.aiscam.antiscam.simulation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustPolicyRepository extends JpaRepository<TrustPolicy, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from TrustPolicy p where p.id = :id")
    java.util.Optional<TrustPolicy> lockById(@org.springframework.data.repository.query.Param("id") int id);
}
