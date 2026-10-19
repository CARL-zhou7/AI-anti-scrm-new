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

    public static ApiException reportNotApplicable() {
        return new ApiException(409, "REPORT_NOT_APPLICABLE", "该演绎不具备报告重试条件");
    }

    public static ApiException activeSimulationExists(String activeSessionId) {
        return new ApiException(409, "ACTIVE_SIMULATION_EXISTS", "你已有一场进行中的演绎",
                new ErrorDetail(List.of(), activeSessionId, null));
    }

    public static ApiException idempotencyConflict() {
        return new ApiException(409, "IDEMPOTENCY_CONFLICT", "同一开始请求不能用于不同配置");
    }

    public static ApiException profileUnavailable() {
        return new ApiException(409, "PROFILE_UNAVAILABLE", "所选画像已不可用，请返回重新选择");
    }

    public static ApiException timingNotReady() {
        return new ApiException(503, "TIMING_PROTOCOL_NOT_READY", "计时校准暂不可用，请稍后重试");
    }

    public static ApiException serviceUnavailable() {
        return new ApiException(503, "SERVICE_UNAVAILABLE", "服务正在恢复，请稍后重试");
    }

    public static ApiException stageConflict() {
        return new ApiException(409, "STAGE_CONFLICT", "当前演绎阶段不接受该回执");
    }

    public static ApiException presentationConflict() {
        return new ApiException(409, "PRESENTATION_CONFLICT", "该对象已有相反的呈现结果");
    }

    public static ApiException audioNotReady() {
        return new ApiException(409, "AUDIO_NOT_READY", "语音尚未就绪");
    }

    public static ApiException audioExpired() {
        return new ApiException(410, "AUDIO_EXPIRED", "语音已失效");
    }
}
