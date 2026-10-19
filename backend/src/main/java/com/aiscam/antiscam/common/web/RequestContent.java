package com.aiscam.antiscam.common.web;

import com.aiscam.antiscam.common.error.ApiException;

public final class RequestContent {
    // shortcut: provisional limit for MySQL TEXT (UTF-8 <= 64000 bytes); replace when TBD-09 is agreed.
    public static final int MAX_TEXT_CHARS = 16000;
    private RequestContent() {}

    public static void rejectBody(String body) {
        if (body != null && !body.isBlank()) throw ApiException.field("body", "UNKNOWN_FIELD", "该接口不接受请求体");
    }

    public static void checkField(String field, String value, int maxChars) {
        if (value == null || value.isBlank()) throw ApiException.field(field, "REQUIRED", "此项不能为空");
        if (value.codePointCount(0, value.length()) > maxChars) {
            throw ApiException.field(field, "LENGTH_INVALID", "此项不能超过" + maxChars + "个字符");
        }
    }
}
