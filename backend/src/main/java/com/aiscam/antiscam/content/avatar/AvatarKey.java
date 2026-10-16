package com.aiscam.antiscam.content.avatar;

import com.aiscam.antiscam.common.error.ApiException;

public final class AvatarKey {
    private AvatarKey() {}

    public static boolean isValid(String key) {
        return key != null && key.matches("avatars/[0-9a-f]{64}\\.(png|jpg)");
    }

    public static String publicKey(String storedKey) {
        if (!isValid(storedKey)) throw ApiException.dataInconsistent("头像资源键不正确");
        return storedKey.substring(8, 72);
    }

    public static void validatePublicKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]+")) {
            throw ApiException.field("key", "VALUE_INVALID", "头像标识格式不正确");
        }
    }
}
