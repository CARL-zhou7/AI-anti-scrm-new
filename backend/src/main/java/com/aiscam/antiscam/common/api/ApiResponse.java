package com.aiscam.antiscam.common.api;

import com.aiscam.antiscam.common.web.RequestIds;

public record ApiResponse<T>(String code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "成功", data, RequestIds.current());
    }

    public static ApiResponse<Object> error(String code, String message, Object data) {
        return new ApiResponse<>(code, message, data, RequestIds.current());
    }
}
