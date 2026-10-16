package com.aiscam.antiscam.common.error;

import com.aiscam.antiscam.common.api.ApiResponse;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
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
        return api(new ApiException(400, "VALIDATION_FAILED", "请求体格式或字段类型不正确"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> missing() {
        return api(new ApiException(404, "NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<?> method() {
        return api(new ApiException(400, "VALIDATION_FAILED", "请求方法或内容类型不支持"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected() {
        return api(new ApiException(500, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
