package com.guyi.access.controller;

import com.guyi.access.dto.ApiResponse;
import com.guyi.access.dto.LoginRequest;
import com.guyi.access.exception.AccountLockedException;
import com.guyi.access.exception.BusinessException;
import com.guyi.access.service.AuthService;
import com.guyi.access.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        String password = request.getPassword();
        // Absent credentials used to reach BCryptPasswordEncoder.matches(null, hash) and return its
        // internal message to the client; reject them as a plain parameter error instead.
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "请输入用户名和密码"));
        }
        try {
            String token = authService.login(username, password, ClientIpUtil.resolve(httpRequest, trustProxy));
            return ResponseEntity.ok(ApiResponse.success("登录成功", Map.of(
                    "token", token,
                    "username", username
            )));
        } catch (AccountLockedException e) {
            log.warn("Login blocked by lockout: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(429, e.getMessage()));
        } catch (BusinessException e) {
            log.info("Login rejected: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(401, e.getMessage()));
        } catch (Exception e) {
            // Logged and masked: echoing e.getMessage() leaked internals ("rawPassword cannot be
            // null", signing-key size errors) to anonymous callers and left no trace.
            log.error("Login failed unexpectedly", e);
            return ResponseEntity.ok(ApiResponse.error(500, "登录失败，请稍后重试"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(ApiResponse.error(401, "未登录"));
        }
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
                "username", authentication.getName()
        )));
    }

    /**
     * Revokes every issued token (the admin row's token_version is bumped), so a copied token stops
     * working immediately instead of surviving until its 3-day expiry.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        authService.revokeAllTokens();
        return ResponseEntity.ok(ApiResponse.success("已退出登录，所有已签发令牌已失效"));
    }
}
