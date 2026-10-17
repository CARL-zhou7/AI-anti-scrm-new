package com.aiscam.antiscam.admin.web.dto;

import com.aiscam.antiscam.content.domain.*;

public record AdminScenarioView(String id, ScenarioCode code, String name, String description, String focus,
        int version, String createdAt, String updatedAt) {
    public static AdminScenarioView from(FraudScenario s) {
        return new AdminScenarioView(s.getId().toString(), s.getScenarioCode(), s.getName(), s.getDescription(),
                s.getScenarioCode().focus(), s.getRowVersion(), AdminViews.time(s.getCreatedAt()), AdminViews.time(s.getUpdatedAt()));
    }
}
