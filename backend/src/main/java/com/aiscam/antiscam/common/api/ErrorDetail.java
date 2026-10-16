package com.aiscam.antiscam.common.api;

import java.util.List;

public record ErrorDetail(List<FieldError> fieldErrors, String activeSessionId, Integer currentVersion) {
    public record FieldError(String field, String code, String message) {}
}
