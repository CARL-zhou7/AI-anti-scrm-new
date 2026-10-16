package com.aiscam.antiscam.auth.web.dto;

public record RegisterRequest(String username, String password, String confirmPassword) {
    @Override public String toString() { return "RegisterRequest[REDACTED]"; }
}
