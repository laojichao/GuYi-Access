package com.guyi.access.controller;

import com.guyi.access.dto.ApiResponse;
import com.guyi.access.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public liveness/readiness probe (design Task 19). Deliberately tiny: it reports only whether the
 * process can reach the database, whether the system has been installed yet, and the current time -
 * no build metadata, no configuration, no secrets.
 *
 * <p>Answers HTTP 200 while the database is reachable and HTTP 503 otherwise, so a plain HTTP monitor
 * works; {@code data.status} carries the same information for API clients.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;

    @Value("${spring.application.name:guyi-access}")
    private String applicationName;

    public HealthController(JdbcTemplate jdbcTemplate, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        boolean dbUp;
        try {
            Integer probe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbUp = probe != null && probe == 1;
        } catch (Exception e) {
            dbUp = false;
        }

        // Only ask whether an administrator exists when the database actually answered: the install
        // wizard must never be shown just because the database is temporarily unreachable.
        boolean installed = false;
        if (dbUp) {
            try {
                installed = authService.isInstalled();
            } catch (Exception ignored) {
                // treated as "unknown" -> false, and the caller also sees status=UP only if dbUp
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", dbUp ? "UP" : "DOWN");
        data.put("db", dbUp ? "UP" : "DOWN");
        data.put("installed", installed);
        data.put("app", applicationName);
        data.put("time", OffsetDateTime.now().toString());

        if (!dbUp) {
            ApiResponse<Map<String, Object>> body = ApiResponse.success("数据库不可用", data);
            body.setCode(503);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }
}
