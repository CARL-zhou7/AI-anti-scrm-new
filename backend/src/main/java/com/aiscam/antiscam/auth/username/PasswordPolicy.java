package com.aiscam.antiscam.auth.username;

import com.aiscam.antiscam.common.error.ApiException;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        int length = password == null ? 0 : password.codePointCount(0, password.length());
        if (length < 6 || length > 24) {
            throw ApiException.field("password", "LENGTH_INVALID", "密码须为6—24个字符，空格会原样保留");
        }
    }

    public static void validate(String password, String confirmPassword) {
        validate(password);
        if (!password.equals(confirmPassword)) {
            throw ApiException.field("confirmPassword", "PASSWORD_MISMATCH", "两次密码输入不一致");
        }
    }
}
