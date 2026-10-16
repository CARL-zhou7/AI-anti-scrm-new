package com.aiscam.antiscam.content.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface ScammerProfileRepository extends Repository<ScammerProfile, Long> {
    List<ScammerProfile> findByIdGreaterThanAndEnabledOrderByIdAsc(long id, byte enabled, Pageable page);
    Optional<ScammerProfile> findByIdAndEnabled(long id, byte enabled);
    List<ScammerProfile> findByEnabled(byte enabled);
    boolean existsByAvatarKeyAndEnabled(String avatarKey, byte enabled);
}
