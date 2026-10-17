package com.aiscam.antiscam.admin.web.dto;

import com.aiscam.antiscam.content.avatar.AvatarKey;
import com.aiscam.antiscam.content.web.dto.AvatarRef;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

final class AdminViews {
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private AdminViews() {}
    static String time(Instant instant) { return TIME.format(instant); }
    static AvatarRef avatar(String storedKey) {
        String key = AvatarKey.publicKey(storedKey);
        return new AvatarRef(key, "/anti.scam/v1/admin/avatars/" + key);
    }
}
