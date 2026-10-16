package com.aiscam.antiscam.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sys_user")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 24)
    private String username;
    @Column(name = "username_key", insertable = false, updatable = false, length = 24)
    private String usernameKey;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private UserRole role;
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;

    protected User() {}

    public User(String username, String passwordHash, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
}
