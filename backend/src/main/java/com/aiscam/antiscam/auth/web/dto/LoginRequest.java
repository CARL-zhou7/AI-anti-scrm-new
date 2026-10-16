package com.aiscam.antiscam.auth.web.dto;

public record LoginRequest(String username, String password) {
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
