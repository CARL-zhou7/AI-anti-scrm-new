package com.aiscam.antiscam.content.domain;

public record VictimProfileValues(String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level antiFraudAwareness, Level riskTendency, String personalityTraits, String avatarKey, String speakingAvatarKey) {}
