package com.aiscam.antiscam.content.web.dto;

import com.aiscam.antiscam.content.avatar.AvatarKey;

public record AvatarRef(String key, String url) {
    public static AvatarRef from(String storedKey) {
        String key = AvatarKey.publicKey(storedKey);
        return new AvatarRef(key, "/anti.scam/v1/avatars/" + key);
    }
}
