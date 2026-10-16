package com.aiscam.antiscam.content.web.dto;

import com.aiscam.antiscam.content.domain.ScenarioCode;

public record ScenarioView(String id, ScenarioCode code, String name, String description, String focus) {}
