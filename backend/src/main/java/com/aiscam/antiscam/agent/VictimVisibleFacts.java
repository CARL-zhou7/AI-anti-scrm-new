package com.aiscam.antiscam.agent;

import java.util.List;

public record VictimVisibleFacts(String name, int age, String gender, String identity, String description,
        String familyStatus, String incomeDescription, String generalDecisionTraits, String currentState,
        List<String> receivedMaterials, List<String> openItems, List<String> resolvedItems) {}
