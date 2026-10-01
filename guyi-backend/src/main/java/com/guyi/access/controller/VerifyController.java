package com.guyi.access.controller;

import com.guyi.access.dto.ApiResponse;
import com.guyi.access.dto.VerifyRequest;
import com.guyi.access.entity.Application;
import com.guyi.access.entity.AppVariable;
import com.guyi.access.entity.Card;
import com.guyi.access.service.ApplicationService;
import com.guyi.access.service.BlacklistService;
import com.guyi.access.service.CardService;
import com.guyi.access.service.SystemService;
import com.guyi.access.util.AesUtil;
import com.guyi.access.util.ClientIpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VerifyController {

    private final CardService cardService;
    private final ApplicationService applicationService;
    private final BlacklistService blacklistService;
    private final SystemService systemService;
    private final ObjectMapper objectMapper;

    @Value("${app.api-token}")
    private String adminApiToken;

    public VerifyController(CardService cardService,
                            ApplicationService applicationService,
                            BlacklistService blacklistService,
                            SystemService systemService,
                            ObjectMapper objectMapper) {
        this.cardService = cardService;
        this.applicationService = applicationService;
        this.blacklistService = blacklistService;
        this.systemService = systemService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String action = request.getAction() != null ? request.getAction() : "verify";

        // Length guards matching the column sizes: over-long values would otherwise reach the
        // database and come back as a generic 500.
        String rawCard = request.getEffectiveCardCode();
        if (rawCard != null && rawCard.length() > 50) {
            return ok(ApiResponse.error(400, "卡密格式无效"));
        }
        String rawDevice = request.getEffectiveDevice();
        if (rawDevice != null && rawDevice.length() > 100) {
            return ok(ApiResponse.error(400, "设备特征码格式无效"));
        }
        if (request.getCustomData() != null && request.getCustomData().length() > 60000) {
            return ok(ApiResponse.error(400, "custom_data 过长"));
        }

        try {
            // Admin API actions
            if ("generate".equals(action) || "ban".equals(action) || "unban".equals(action)
                    || "del_card".equals(action) || "kick".equals(action)) {
                return handleAdminAction(request, clientIp);
            }

            // Blacklist action. This branch used to run before any app_key check, so an anonymous
            // caller could permanently blacklist an arbitrary device hash across EVERY application.
            // It now requires a valid app_key plus a device the caller provably owns.
            if ("ban_machine".equals(action) || "blacklist".equals(action)) {
                String device = request.getEffectiveDevice();
                if (device == null || device.isEmpty()) {
                    return ok(ApiResponse.error(400, "未提供需要拉黑的设备特征码(device_hash)"));
                }
                if (device.length() > 100) {
                    return ok(ApiResponse.error(400, "设备特征码格式无效"));
                }
                Application banApp = applicationService.getAppByKey(request.getAppKey());
                if (banApp == null || banApp.getStatus() == 0) {
                    return ok(ApiResponse.error(403, "无权拉黑：缺少有效的 AppKey"));
                }
                if (!cardService.isDeviceOwnedByApp(device, banApp.getId(), request.getEffectiveCardCode())) {
                    return ok(ApiResponse.error(403, "无权拉黑该设备：请先完成一次有效验证"));
                }
                blacklistService.addDeviceAndIpBlacklist(device, clientIp, "触发客户端安全防御策略");
                return ok(ApiResponse.success("设备与所在IP已被系统成功拉黑"));
            }

            // Unbind action
            if ("unbind".equals(action)) {
                String cardCode = request.getEffectiveCardCode();
                if (cardCode == null || cardCode.isEmpty()) {
                    return ok(ApiResponse.error(400, "解绑失败：必须提供卡密(card_code)"));
                }
                boolean result = cardService.unbindCardByApi(cardCode);
                if (result) {
                    return ok(ApiResponse.success("解绑成功，作为代价已扣除 12 小时使用寿命"));
                } else {
                    return ok(ApiResponse.error(400, "解绑失败：卡密错误、不存在或尚未激活"));
                }
            }

            // Default: verify action
            String appKey = request.getAppKey();
            String cardCode = request.getEffectiveCardCode();
            String device = request.getEffectiveDevice();

            Application appInfo = null;
            Map<String, Object> updateData = null;
            if (appKey != null && !appKey.isEmpty()) {
                appInfo = applicationService.getAppByKey(appKey);
                if (appInfo == null) {
                    return ok(ApiResponse.error(403, "AppKey 错误或不存在"));
                }
                // HashMap instead of Map.of: Map.of throws NPE when forceUpdate is null (legacy DB rows)
                updateData = new LinkedHashMap<>();
                updateData.put("version", appInfo.getAppVersion() != null ? appInfo.getAppVersion() : "");
                updateData.put("url", appInfo.getUpdateUrl() != null ? appInfo.getUpdateUrl() : "");
                updateData.put("log", appInfo.getNotes() != null ? appInfo.getNotes() : "");
                updateData.put("force", appInfo.getForceUpdate() != null ? appInfo.getForceUpdate() : 0);
            }

            // Only app_key, no card_code -> return variables
            if ((cardCode == null || cardCode.isEmpty()) && appKey != null && !appKey.isEmpty()) {
                List<AppVariable> vars = applicationService.getVariables(appInfo.getId(), true);
                Map<String, String> variables = new LinkedHashMap<>();
                for (AppVariable v : vars) {
                    variables.put(v.getKeyName(), v.getValue());
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("update", updateData);
                data.put("variables", variables.isEmpty() ? null : variables);
                return encryptedResponse(200, "OK", data, appKey);
            }

            if (cardCode == null || cardCode.isEmpty()) {
                return ok(ApiResponse.error(400, "请输入卡密"));
            }
            if (device == null || device.isEmpty()) {
                device = md5(clientIp);
            }

            // Core verification (reuses the already-fetched app instead of re-querying by key)
            Map<String, Object> result = cardService.verifyCard(
                    cardCode, device, appInfo, request.getCustomData(), clientIp, userAgent);

            if (Boolean.TRUE.equals(result.get("success"))) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("expire_time", result.get("expire_time"));
                data.put("custom_data", result.getOrDefault("custom_data", ""));
                data.put("update", updateData);

                Integer appId = (Integer) result.get("app_id");
                List<AppVariable> allVars = applicationService.getVariables(appId, false);
                Map<String, String> variables = new LinkedHashMap<>();
                for (AppVariable v : allVars) {
                    variables.put(v.getKeyName(), v.getValue());
                }
                data.put("variables", variables);

                return encryptedResponse(200, "OK", data, appKey);
            } else {
                return ok(ApiResponse.error(403, (String) result.get("message")));
            }

        } catch (Exception e) {
            log.error("Verify request failed: action={}, ip={}", action, clientIp, e);
            return ok(ApiResponse.error(500, "服务器内部错误"));
        }
    }

    private ResponseEntity<?> handleAdminAction(VerifyRequest request, String clientIp) {
        // Constant-time comparison to prevent timing attacks
        String provided = request.getApiToken();
        if (provided == null || !java.security.MessageDigest.isEqual(
                adminApiToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (provided != null ? provided : "").getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return ok(ApiResponse.error(403, "无权操作：对接通信密钥(api_token)错误或未提供！"));
        }

        String action = request.getAction();

        if ("generate".equals(action)) {
            try {
                Integer appId = request.getAppId();
                if (appId == null || appId <= 0) {
                    if (request.getAppKey() != null && !request.getAppKey().isEmpty()) {
                        Application app = applicationService.getAppByKey(request.getAppKey());
                        if (app != null) appId = app.getId();
                    }
                }
                if (appId == null || appId <= 0) {
                    return ok(ApiResponse.error(400, "生成失败：请提供有效的 app_id 或 app_key"));
                }
                int num = request.getNum() != null ? request.getNum() : 1;
                if (num < 1 || num > 500) {
                    return ok(ApiResponse.error(400, "生成失败：单次生成数量须在 1-500 之间"));
                }
                String type = request.getType() != null ? request.getType() : "day";
                String pre = request.getPre() != null ? request.getPre() : "";
                String note = request.getNote() != null ? request.getNote() : "API接口批量生卡";
                // Absent or non-positive custom_hours means "standard card type" (matching the PHP
                // backend), so only an over-sized value is rejected; a positive value wins.
                Double customHours = request.getCustomHours();
                if (customHours != null && customHours > 8760) {
                    return ok(ApiResponse.error(400, "生成失败：自定义时长不能超过 8760 小时"));
                }
                int customDuration = customHours != null && customHours > 0 ? (int) (customHours * 3600) : 0;

                List<String> codes = cardService.generateCards(num, type, pre, note, appId, customDuration);
                String cardStr = String.join("\n", codes);
                return ok(ApiResponse.success("成功生成 " + num + " 张卡密",
                        Map.of("cards", codes, "card_string", cardStr)));
            } catch (Exception e) {
                return ok(ApiResponse.error(500, "生成失败"));
            }
        }

        String cardCode = request.getEffectiveCardCode();
        if (cardCode == null || cardCode.isEmpty()) {
            return ok(ApiResponse.error(400, "缺少要操作的卡密(card_code)参数"));
        }

        // Find card by exact code (uses unique index, not fuzzy search)
        Card card = cardService.getCardByCode(cardCode).orElse(null);

        if (card == null) {
            return ok(ApiResponse.error(404, "该卡密不存在于数据库中"));
        }

        switch (action) {
            case "ban" -> {
                cardService.updateCardStatus(card.getId(), 2);
                return ok(ApiResponse.success("卡密已成功封禁"));
            }
            case "unban" -> {
                cardService.updateCardStatus(card.getId(), 1);
                return ok(ApiResponse.success("卡密已解除封禁，恢复正常"));
            }
            case "del_card" -> {
                cardService.deleteCard(card.getId());
                return ok(ApiResponse.success("卡密已成功彻底删除"));
            }
            case "kick" -> {
                cardService.resetDeviceBinding(card.getId());
                return ok(ApiResponse.success("卡密已强制踢下线并成功解绑设备"));
            }
            default -> {
                return ok(ApiResponse.error(400, "未知操作"));
            }
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VerifyController.class);

    private ResponseEntity<?> encryptedResponse(int code, String msg, Object data, String appKey) {
        String json;
        try {
            json = objectMapper.writeValueAsString(ApiResponse.success(msg, data));
        } catch (Exception e) {
            log.error("Failed to serialize response", e);
            return ResponseEntity.ok(ApiResponse.success(msg, data));
        }

        String encryptEnabled = "1";
        try {
            Map<String, String> settings = systemService.getSystemSettings();
            encryptEnabled = settings.getOrDefault("api_encrypt", "1");
        } catch (Exception ignored) {}

        if ("1".equals(encryptEnabled) && appKey != null && appKey.length() == 64) {
            try {
                String encrypted = AesUtil.encrypt(json, appKey);
                return ResponseEntity.ok(Map.of("encrypted_data", encrypted));
            } catch (Exception e) {
                log.warn("AES encryption failed for appKey={}..., falling back to plaintext", appKey.substring(0, 8), e);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(msg, data));
    }

    private ResponseEntity<?> ok(ApiResponse<?> response) {
        return ResponseEntity.ok(response);
    }

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    private String getClientIp(HttpServletRequest request) {
        return ClientIpUtil.resolve(request, trustProxy);
    }

    private String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
