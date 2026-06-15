package com.guyi.access.controller;

import com.guyi.access.dto.ApiResponse;
import com.guyi.access.dto.LoginRequest;
import com.guyi.access.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String token = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(ApiResponse.success("登录成功", Map.of(
                    "token", token,
                    "username", request.getUsername()
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(ApiResponse.error(401, e.getMessage()));
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
}
