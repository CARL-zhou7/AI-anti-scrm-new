package com.aiscam.antiscam.content.domain;

public record ScammerProfileValues(String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level fraudExperience, Level speechSkill, Level manipulationSkill, String behaviorTraits, String avatarKey, String speakingAvatarKey) {}
