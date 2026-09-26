package com.guyi.access.config;

import com.guyi.access.dto.ApiResponse;
import com.guyi.access.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.ok(ApiResponse.error(413, "文件过大"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
    }

    // Expected business rule violations: surface the real message to the client
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException e) {
        log.info("Business rule violation: {}", e.getMessage());
        return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        log.warn("Runtime exception: {}", e.getMessage());
        return ResponseEntity.ok(ApiResponse.error(400, "请求处理失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.ok(ApiResponse.error(500, "服务器内部错误"));
    }
}
