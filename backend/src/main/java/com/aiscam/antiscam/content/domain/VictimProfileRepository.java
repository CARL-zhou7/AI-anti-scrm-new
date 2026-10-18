package com.aiscam.antiscam.content.domain;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface VictimProfileRepository extends Repository<VictimProfile, Long> {
    List<VictimProfile> findByIdGreaterThanAndEnabledOrderByIdAsc(long id, byte enabled, Pageable page);
    Optional<VictimProfile> findByIdAndEnabled(long id, byte enabled);
    List<VictimProfile> findByEnabled(byte enabled);
    boolean existsByAvatarKeyAndEnabled(String avatarKey, byte enabled);
    boolean existsBySpeakingAvatarKeyAndEnabled(String speakingAvatarKey, byte enabled);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from VictimProfile p where p.id = :id")
    Optional<VictimProfile> shareLockById(@Param("id") long id);
    Optional<VictimProfile> findById(long id);
    VictimProfile save(VictimProfile profile);
    List<VictimProfile> findByIdGreaterThanOrderByIdAsc(long id, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from VictimProfile p where p.id = :id")
    Optional<VictimProfile> lockById(@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from VictimProfile p order by p.id asc")
    List<VictimProfile> lockAllByOrderByIdAsc();
}
