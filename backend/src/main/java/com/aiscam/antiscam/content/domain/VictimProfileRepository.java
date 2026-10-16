package com.aiscam.antiscam.content.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface VictimProfileRepository extends Repository<VictimProfile, Long> {
    List<VictimProfile> findByIdGreaterThanAndEnabledOrderByIdAsc(long id, byte enabled, Pageable page);
    Optional<VictimProfile> findByIdAndEnabled(long id, byte enabled);
    List<VictimProfile> findByEnabled(byte enabled);
    boolean existsByAvatarKeyAndEnabled(String avatarKey, byte enabled);
}
