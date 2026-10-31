package com.aiscam.antiscam.agent;

import java.util.List;

public record ScammerVisibleProfile(int age, String gender, String identity, String description, String contactBackground,
        List<String> sharedExperiences, List<String> disclosedFacts) {}
