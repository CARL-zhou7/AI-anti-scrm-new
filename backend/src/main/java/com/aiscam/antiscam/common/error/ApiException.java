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
}
