package com.aiscam.antiscam.content.web.dto;

import com.aiscam.antiscam.content.domain.*;

public record ScammerView(String id, AvatarRef avatar, String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level fraudExperience, Level speechSkill, Level manipulationSkill, String behaviorTraits) {
    public static ScammerView from(ScammerProfile p) {
        return new ScammerView(p.getId().toString(), AvatarRef.from(p.getAvatarKey()), p.getName(), p.getAge(), p.getGender(), p.getIdentity(), p.getDescription(), p.getContextTime(), p.getContextPlace(), p.getCurrentState(), p.getFraudExperience(), p.getSpeechSkill(), p.getManipulationSkill(), p.getBehaviorTraits());
    }
}
