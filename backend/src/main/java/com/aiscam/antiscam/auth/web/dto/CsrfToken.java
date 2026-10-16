package com.aiscam.antiscam.auth.web.dto;

public record CsrfToken(String token, String headerName) {
    @Override public String toString() { return "CsrfToken[REDACTED]"; }
}
