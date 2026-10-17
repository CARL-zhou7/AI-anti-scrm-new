package com.aiscam.antiscam.common.error;

import com.aiscam.antiscam.common.api.ApiResponse;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import com.aiscam.antiscam.common.api.ErrorDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> api(ApiException ex) {
        return ResponseEntity.status(ex.status).body(ApiResponse.error(ex.code, ex.getMessage(), ex.detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadable(HttpMessageNotReadableException ex) {
        if (ex.getMostSpecificCause() instanceof UnrecognizedPropertyException unknown) {
            return api(ApiException.field(unknown.getPropertyName(), "UNKNOWN_FIELD", "不允许提交此字段"));
        }
        if (ex.getCause() instanceof MismatchedInputException invalid && !invalid.getPath().isEmpty()) {
            String field = invalid.getPath().getLast().getFieldName();
            if (field != null) return api(ApiException.field(field, "TYPE_INVALID", "字段类型或取值不正确"));
        }
        return api(new ApiException(400, "VALIDATION_FAILED", "请求体格式或字段类型不正确"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream().map(error -> {
            String code = switch (error.getCode()) {
                case "NotNull", "NotBlank" -> "REQUIRED";
                case "Size" -> "LENGTH_INVALID";
                default -> "VALUE_INVALID";
            };
            return new ErrorDetail.FieldError(error.getField(), code, error.getDefaultMessage());
        }).toList();
        return api(new ApiException(400, "VALIDATION_FAILED", "请检查输入信息", new ErrorDetail(fields, null, null)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge() { return api(ApiException.fileTooLarge()); }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<?> multipart() { return api(ApiException.field("file", "VALUE_INVALID", "请上传一个有效头像文件")); }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<?> lockFailure() {
        return api(new ApiException(503, "SERVICE_UNAVAILABLE", "内容正在被修改，请重新读取后重试"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> missing() {
        return api(new ApiException(404, "NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method() {
        return api(new ApiException(400, "VALIDATION_FAILED", "请求方法或内容类型不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> mediaType(jakarta.servlet.http.HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/anti.scam/v1/admin/")) {
            return api(new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "不支持的请求或图片格式"));
        }
        return method();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected() {
        return api(new ApiException(500, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
