package com.aiscam.antiscam.auth.web.dto;

import com.aiscam.antiscam.auth.domain.User;
import com.aiscam.antiscam.auth.domain.UserRole;
import java.io.Serializable;

public record Account(String id, String username, UserRole role) implements Serializable {
    public static Account from(User user) {
        return new Account(user.getId().toString(), user.getUsername(), user.getRole());
    }
}
