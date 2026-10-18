package com.aiscam.antiscam.content.web.dto;

import com.aiscam.antiscam.content.domain.*;

public record VictimView(String id, AvatarRef avatar, AvatarRef speakingAvatar, String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level antiFraudAwareness, Level riskTendency, String personalityTraits) {
    public static VictimView from(VictimProfile p) {
        return new VictimView(p.getId().toString(), AvatarRef.from(p.getAvatarKey()), AvatarRef.from(p.getSpeakingAvatarKey()), p.getName(), p.getAge(), p.getGender(), p.getIdentity(), p.getDescription(), p.getContextTime(), p.getContextPlace(), p.getCurrentState(), p.getAntiFraudAwareness(), p.getRiskTendency(), p.getPersonalityTraits());
    }
}
