package com.aiscam.antiscam.common.error;

import com.aiscam.antiscam.common.api.ErrorDetail;
import java.util.List;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public final Object detail;

    public ApiException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, String code, String message, Object detail) {
        super(message);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public static ApiException field(String field, String code, String message) {
        return new ApiException(400, "VALIDATION_FAILED", "请检查输入信息",
                new ErrorDetail(List.of(new ErrorDetail.FieldError(field, code, message)), null, null));
    }

    public static ApiException invalidCursor() {
        return new ApiException(400, "INVALID_CURSOR", "分页游标无效");
    }

    public static ApiException versionConflict(int currentVersion) {
        return new ApiException(409, "VERSION_CONFLICT", "内容已被修改，请重新读取",
                new ErrorDetail(List.of(), null, currentVersion));
    }

    public static ApiException lastEnabledProfile() {
        return new ApiException(409, "LAST_ENABLED_PROFILE", "该类必须保留至少一个可用画像");
    }

    public static ApiException imageInvalid() {
        return new ApiException(400, "INVALID_IMAGE", "图片内容无效或不是静态JPG/PNG");
    }

    public static ApiException fileTooLarge() {
        return new ApiException(413, "FILE_TOO_LARGE", "头像超过允许大小");
    }

    public static ApiException notFound() {
        return new ApiException(404, "NOT_FOUND", "资源不存在或不可访问");
    }

    public static ApiException dataInconsistent(String message) {
        return new ApiException(500, "DATA_INCONSISTENT", message);
    }
}
