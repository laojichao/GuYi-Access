package com.guyi.access.config;

import com.guyi.access.dto.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers container-level errors (unknown path, wrong method, oversized upload) with the same JSON
 * envelope as every other endpoint instead of Spring Boot's HTML "whitelabel" page.
 *
 * <p>Business errors keep returning HTTP 200 with a {@code code} field (the admin frontend reads that
 * field), but a mistyped URL or a rejected method is a transport-level problem and reports a real
 * HTTP status. The exception message is never echoed: it may carry implementation detail.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> handleError(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer ? (Integer) statusAttribute : 500;
        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            status = 500;
        }

        String message = switch (status) {
            case 400 -> "请求参数无法处理";
            case 401 -> "未登录或登录已过期";
            case 403 -> "无权访问";
            case 404 -> "接口不存在";
            case 405 -> "请求方法不允许";
            case 413 -> "上传内容过大";
            default -> httpStatus.is5xxServerError() ? "服务器内部错误" : "请求无法处理";
        };

        return ResponseEntity.status(status).body(ApiResponse.error(status, message));
    }
}
