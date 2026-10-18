package com.aiscam.antiscam.admin.web.dto;

import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.content.web.dto.AvatarRef;

public record AdminScammerView(String id, AvatarRef avatar, AvatarRef speakingAvatar, String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level fraudExperience, Level speechSkill, Level manipulationSkill, String behaviorTraits, boolean enabled, int version, String createdAt, String updatedAt) {
    public static AdminScammerView from(ScammerProfile p) {
        return new AdminScammerView(p.getId().toString(), AdminViews.avatar(p.getAvatarKey()), AdminViews.avatar(p.getSpeakingAvatarKey()), p.getName(), p.getAge(), p.getGender(), p.getIdentity(), p.getDescription(), p.getContextTime(), p.getContextPlace(), p.getCurrentState(), p.getFraudExperience(), p.getSpeechSkill(), p.getManipulationSkill(), p.getBehaviorTraits(), p.isEnabled(), p.getRowVersion(), AdminViews.time(p.getCreatedAt()), AdminViews.time(p.getUpdatedAt()));
    }
}
