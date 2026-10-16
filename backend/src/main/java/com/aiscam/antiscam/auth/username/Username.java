package com.aiscam.antiscam.auth.username;

import com.aiscam.antiscam.common.error.ApiException;
import java.util.Locale;

public final class Username {
    private Username() {}

    public static String normalize(String raw) {
        return raw == null ? "" : raw.strip();
    }

    public static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public static void validate(String username) {
        int length = username.codePointCount(0, username.length());
        if (length < 2 || length > 24) {
            throw ApiException.field("username", "LENGTH_INVALID", "用户名须为2—24个字符");
        }
        if (!username.matches("[\\u4E00-\\u9FFFA-Za-z0-9_]+")) {
            throw ApiException.field("username", "VALUE_INVALID", "用户名仅允许汉字、英文字母、数字和下划线");
        }
    }
}
