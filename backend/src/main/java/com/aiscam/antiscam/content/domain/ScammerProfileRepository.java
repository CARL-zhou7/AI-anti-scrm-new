package com.aiscam.antiscam.content.domain;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface ScammerProfileRepository extends Repository<ScammerProfile, Long> {
    List<ScammerProfile> findByIdGreaterThanAndEnabledOrderByIdAsc(long id, byte enabled, Pageable page);
    Optional<ScammerProfile> findByIdAndEnabled(long id, byte enabled);
    List<ScammerProfile> findByEnabled(byte enabled);
    boolean existsByAvatarKeyAndEnabled(String avatarKey, byte enabled);
    boolean existsBySpeakingAvatarKeyAndEnabled(String speakingAvatarKey, byte enabled);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from ScammerProfile p where p.id = :id")
    Optional<ScammerProfile> shareLockById(@Param("id") long id);
    Optional<ScammerProfile> findById(long id);
    ScammerProfile save(ScammerProfile profile);
    List<ScammerProfile> findByIdGreaterThanOrderByIdAsc(long id, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ScammerProfile p where p.id = :id")
    Optional<ScammerProfile> lockById(@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ScammerProfile p order by p.id asc")
    List<ScammerProfile> lockAllByOrderByIdAsc();
}
