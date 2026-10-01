package com.guyi.access.config;

import com.guyi.access.dto.ApiResponse;
import com.guyi.access.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Error envelope for the API.
 *
 * <p>Two deliberate categories:
 * <ul>
 *   <li><b>Business/parameter errors</b> answer HTTP 200 with a {@code code} field, because the admin
 *       frontend reads {@code response.data.code} (and shows {@code msg}) rather than relying on the
 *       HTTP status.</li>
 *   <li><b>Transport-level errors</b> (unknown path, wrong method, unsupported media type, oversized
 *       upload) answer the real HTTP status with the same JSON envelope.</li>
 * </ul>
 *
 * <p>Transport-level cases need explicit handlers: without them the catch-all below swallowed e.g.
 * {@code NoResourceFoundException} and reported "服务器内部错误" with HTTP 200 for a plain 404.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload rejected, too large: {}", e.getMessage());
        return ResponseEntity.status(413).body(ApiResponse.error(413, "文件过大"));
    }

    /** No controller and no static resource for this path. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<?> handleNotFound(Exception e) {
        log.warn("No handler for request: {}", e.getMessage());
        return ResponseEntity.status(404).body(ApiResponse.error(404, "接口不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ResponseEntity.status(405).body(ApiResponse.error(405, "请求方法不允许"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type: {}", e.getMessage());
        return ResponseEntity.status(415).body(ApiResponse.error(415, "不支持的内容类型"));
    }

    /** e.g. a non-numeric path variable: a client mistake, so it keeps the 200 + code convention. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Argument type mismatch: {}", e.getMessage());
        return ResponseEntity.ok(ApiResponse.error(400, "请求参数格式错误"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        // IllegalArgumentException covers NumberFormatException and library internals alike, so the
        // raw message is logged rather than echoed (it can leak implementation detail, or be null and
        // then vanish entirely under @JsonInclude(NON_NULL)).
        log.warn("Illegal argument: {}", e.getMessage());
        return ResponseEntity.ok(ApiResponse.error(400, "请求参数格式错误"));
    }

    // Expected business rule violations: surface the real message to the client
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException e) {
        log.info("Business rule violation: {}", e.getMessage());
        return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        // Keep the stack trace: logged as a message alone, an NPE or SQL failure is undiagnosable.
        log.warn("Runtime exception", e);
        return ResponseEntity.ok(ApiResponse.error(400, "请求处理失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.ok(ApiResponse.error(500, "服务器内部错误"));
    }
}
