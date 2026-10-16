package com.aiscam.antiscam.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameKey(String usernameKey);
    boolean existsByUsernameKey(String usernameKey);
}
