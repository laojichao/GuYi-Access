package com.guyi.access.controller;

import com.guyi.access.dto.*;
import com.guyi.access.entity.*;
import com.guyi.access.repository.*;
import com.guyi.access.service.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ApplicationService applicationService;
    private final CardService cardService;
    private final DashboardService dashboardService;
    private final BlacklistService blacklistService;
    private final SystemService systemService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final UsageLogRepository usageLogRepository;

    public AdminController(ApplicationService applicationService,
                           CardService cardService,
                           DashboardService dashboardService,
                           BlacklistService blacklistService,
                           SystemService systemService,
                           AuthService authService,
                           ObjectMapper objectMapper,
                           UsageLogRepository usageLogRepository) {
        this.applicationService = applicationService;
        this.cardService = cardService;
        this.dashboardService = dashboardService;
        this.blacklistService = blacklistService;
        this.systemService = systemService;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.usageLogRepository = usageLogRepository;
    }

    // ========== Install ==========

    @PostMapping("/install")
    public ResponseEntity<?> install(@RequestBody InstallRequest request) {
        if (authService.isInstalled()) {
            return ResponseEntity.ok(ApiResponse.error(400, "系统已安装"));
        }
        try {
            authService.initAdmin("GuYi", request.getAdminPassword());
            String token = authService.login("GuYi", request.getAdminPassword());
            return ResponseEntity.ok(ApiResponse.success("安装成功", Map.of("token", token)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "安装失败"));
        }
    }

    // ========== Dashboard ==========

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("OK", dashboardService.getDashboardData()));
    }

    // ========== Applications ==========

    @GetMapping("/apps")
    public ResponseEntity<?> getApps() {
        return ResponseEntity.ok(ApiResponse.success("OK", applicationService.getAllApps()));
    }

    @PostMapping("/apps")
    public ResponseEntity<?> createApp(@RequestBody Map<String, String> body) {
        try {
            String appKey = applicationService.createApp(
                    body.get("app_name"),
                    body.getOrDefault("app_version", ""),
                    body.getOrDefault("app_notes", "")
            );
            return ResponseEntity.ok(ApiResponse.success("应用创建成功", Map.of("app_key", appKey)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/apps/{id}")
    public ResponseEntity<?> updateApp(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        try {
            applicationService.updateApp(id,
                    toStr(body.get("app_name")),
                    toStr(body.getOrDefault("app_version", "")),
                    toStr(body.getOrDefault("app_notes", "")),
                    toStr(body.getOrDefault("update_url", "")),
                    toInt(body.get("force_update"))
            );
            return ResponseEntity.ok(ApiResponse.success("应用信息已更新"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/apps/{id}/toggle")
    public ResponseEntity<?> toggleApp(@PathVariable Integer id) {
        applicationService.toggleAppStatus(id);
        return ResponseEntity.ok(ApiResponse.success("应用状态已更新"));
    }

    @DeleteMapping("/apps/{id}")
    public ResponseEntity<?> deleteApp(@PathVariable Integer id) {
        try {
            applicationService.deleteApp(id);
            return ResponseEntity.ok(ApiResponse.success("应用已删除"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ========== App Variables ==========

    @GetMapping("/apps/{appId}/variables")
    public ResponseEntity<?> getVariables(@PathVariable Integer appId,
                                           @RequestParam(defaultValue = "false") boolean onlyPublic) {
        return ResponseEntity.ok(ApiResponse.success("OK", applicationService.getVariables(appId, onlyPublic)));
    }

    @PostMapping("/apps/{appId}/variables")
    public ResponseEntity<?> addVariable(@PathVariable Integer appId, @RequestBody Map<String, Object> body) {
        try {
            applicationService.addVariable(appId,
                    toStr(body.get("key")),
                    toStr(body.get("value")),
                    toInt(body.getOrDefault("is_public", 0)));
            return ResponseEntity.ok(ApiResponse.success("变量添加成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PutMapping("/variables/{id}")
    public ResponseEntity<?> updateVariable(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        try {
            applicationService.updateVariable(id,
                    toStr(body.get("key")),
                    toStr(body.get("value")),
                    toInt(body.getOrDefault("is_public", 0)));
            return ResponseEntity.ok(ApiResponse.success("变量更新成功"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
        }
    }

    @DeleteMapping("/variables/{id}")
    public ResponseEntity<?> deleteVariable(@PathVariable Integer id) {
        applicationService.deleteVariable(id);
        return ResponseEntity.ok(ApiResponse.success("变量已删除"));
    }

    // ========== Cards ==========

    @GetMapping("/cards")
    public ResponseEntity<?> getCards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer appId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "create_desc") String sort,
            @RequestParam(required = false) String q) {

        if (q != null && !q.isEmpty()) {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit);
            org.springframework.data.domain.Page<Card> searchPage = cardService.searchCardsPaged(q, pageable);
            Map<Integer, String> appNameMap = applicationService.getAllApps().stream()
                    .collect(java.util.stream.Collectors.toMap(a -> (Integer) a.get("id"), a -> (String) a.get("app_name")));
            List<Map<String, Object>> enrichedCards = searchPage.getContent().stream().map(card -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("id", card.getId());
                map.put("cardCode", card.getCardCode());
                map.put("cardType", card.getCardType());
                map.put("status", card.getStatus());
                map.put("deviceHash", card.getDeviceHash());
                map.put("usedTime", card.getUsedTime());
                map.put("expireTime", card.getExpireTime());
                map.put("createTime", card.getCreateTime());
                map.put("notes", card.getNotes());
                map.put("appId", card.getAppId());
                map.put("appName", appNameMap.getOrDefault(card.getAppId(), "未分类"));
                map.put("customData", card.getCustomData());
                map.put("duration", card.getDuration());
                return map;
            }).collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
                    "total", searchPage.getTotalElements(),
                    "cards", enrichedCards,
                    "page", searchPage.getNumber(),
                    "totalPages", searchPage.getTotalPages()
            )));
        }

        Page<Card> cardPage = cardService.getCardsPaginated(page, limit, status, appId, type, sort);
        // Enrich cards with app names
        Map<Integer, String> appNameMap = applicationService.getAllApps().stream()
                .collect(java.util.stream.Collectors.toMap(a -> (Integer) a.get("id"), a -> (String) a.get("app_name")));
        List<Map<String, Object>> enrichedCards = cardPage.getContent().stream().map(card -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", card.getId());
            map.put("cardCode", card.getCardCode());
            map.put("cardType", card.getCardType());
            map.put("status", card.getStatus());
            map.put("deviceHash", card.getDeviceHash());
            map.put("usedTime", card.getUsedTime());
            map.put("expireTime", card.getExpireTime());
            map.put("createTime", card.getCreateTime());
            map.put("notes", card.getNotes());
            map.put("appId", card.getAppId());
            map.put("appName", appNameMap.getOrDefault(card.getAppId(), "未分类"));
            map.put("customData", card.getCustomData());
            map.put("duration", card.getDuration());
            return map;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
                "total", cardPage.getTotalElements(),
                "cards", enrichedCards,
                "page", cardPage.getNumber(),
                "totalPages", cardPage.getTotalPages()
        )));
    }

    @PostMapping("/cards/generate")
    public ResponseEntity<?> generateCards(@RequestBody GenerateCardsRequest request) {
        try {
            if (request.getNum() == null || request.getNum() < 1) {
                return ResponseEntity.ok(ApiResponse.error(400, "生成数量必须大于0"));
            }
            if (request.getNum() > 500) {
                return ResponseEntity.ok(ApiResponse.error(400, "单次生成数量不能超过500"));
            }
            if (request.getAppId() == null || request.getAppId() <= 0) {
                return ResponseEntity.ok(ApiResponse.error(400, "必须指定有效的应用"));
            }
            int customDuration = request.getCustomHours() != null ? (int) (request.getCustomHours() * 3600) : 0;
            List<String> codes = cardService.generateCards(
                    request.getNum(), request.getType(), request.getPre(),
                    request.getNote(), request.getAppId(), customDuration);
            return ResponseEntity.ok(ApiResponse.success("成功生成 " + codes.size() + " 张卡密",
                    Map.of("cards", codes)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(400, "生成失败"));
        }
    }

    @PostMapping("/cards/batch-delete")
    public ResponseEntity<?> batchDelete(@RequestBody BatchRequest request) {
        int count = cardService.batchDeleteCards(request.getIds());
        return ResponseEntity.ok(ApiResponse.success("已批量删除 " + count + " 张卡密"));
    }

    @PostMapping("/cards/batch-unbind")
    public ResponseEntity<?> batchUnbind(@RequestBody BatchRequest request) {
        int count = cardService.batchUnbindCards(request.getIds());
        return ResponseEntity.ok(ApiResponse.success("已批量解绑 " + count + " 个设备"));
    }

    @PostMapping("/cards/batch-add-time")
    public ResponseEntity<?> batchAddTime(@RequestBody BatchRequest request) {
        int count = cardService.batchAddTime(request.getIds(), request.getHours());
        return ResponseEntity.ok(ApiResponse.success("已为 " + count + " 张卡密增加 " + request.getHours() + " 小时"));
    }

    @PostMapping("/cards/batch-sub-time")
    public ResponseEntity<?> batchSubTime(@RequestBody BatchRequest request) {
        int count = cardService.batchSubTime(request.getIds(), request.getHours());
        return ResponseEntity.ok(ApiResponse.success("已为 " + count + " 张卡密扣除 " + request.getHours() + " 小时"));
    }

    @PostMapping("/cards/global-compensate")
    public ResponseEntity<?> globalCompensate(@RequestBody Map<String, Object> body) {
        if (body.get("hours") == null) {
            return ResponseEntity.ok(ApiResponse.error(400, "缺少 hours 参数"));
        }
        Double hours;
        try {
            hours = Double.parseDouble(body.get("hours").toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.ok(ApiResponse.error(400, "hours 格式无效"));
        }
        if (hours <= 0 || hours > 8760) {
            return ResponseEntity.ok(ApiResponse.error(400, "补偿时长必须在 0-8760 小时之间"));
        }
        Integer appId = body.containsKey("app_id") ? toInt(body.get("app_id")) : null;
        cardService.globalCompensate(hours, appId);
        return ResponseEntity.ok(ApiResponse.success("已成功为所有在用卡密补偿 " + hours + " 小时"));
    }

    @PostMapping("/cards/clean-expired")
    public ResponseEntity<?> cleanExpired() {
        int count = cardService.cleanupExpiredCards();
        return ResponseEntity.ok(ApiResponse.success("已清理 " + count + " 张过期卡密"));
    }

    @PostMapping("/cards/batch-export")
    public void batchExport(@RequestBody BatchRequest request, HttpServletResponse response) throws IOException {
        List<Card> cards = cardService.getCardsByIds(request.getIds());
        response.setContentType("text/plain");
        response.setHeader("Content-Disposition", "attachment; filename=cards_export_" +
                new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".txt");
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            sb.append(card.getCardCode()).append("\r\n");
        }
        response.getWriter().write(sb.toString());
    }

    @PutMapping("/cards/{id}/status")
    public ResponseEntity<?> updateCardStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        cardService.updateCardStatus(id, body.get("status"));
        return ResponseEntity.ok(ApiResponse.success("卡密状态已更新"));
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable Integer id) {
        cardService.deleteCard(id);
        return ResponseEntity.ok(ApiResponse.success("卡密已删除"));
    }

    @PutMapping("/cards/{id}/unbind")
    public ResponseEntity<?> unbindCard(@PathVariable Integer id) {
        cardService.resetDeviceBinding(id);
        return ResponseEntity.ok(ApiResponse.success("设备解绑成功"));
    }

    // ========== Blacklist ==========

    @GetMapping("/blacklist")
    public ResponseEntity<?> getBlacklist() {
        return ResponseEntity.ok(ApiResponse.success("OK", blacklistService.getAll()));
    }

    @PostMapping("/blacklist")
    public ResponseEntity<?> addBlacklist(@RequestBody Map<String, String> body) {
        blacklistService.addBlacklist(
                body.get("type"),
                body.get("value"),
                body.getOrDefault("reason", ""));
        return ResponseEntity.ok(ApiResponse.success("云黑记录已添加"));
    }

    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<?> deleteBlacklist(@PathVariable Integer id) {
        blacklistService.deleteBlacklist(id);
        return ResponseEntity.ok(ApiResponse.success("云黑记录已删除"));
    }

    // ========== Logs ==========

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit);
        return ResponseEntity.ok(ApiResponse.success("OK", usageLogRepository.findAllByOrderByAccessTimeDesc(pageable)));
    }

    // ========== Settings ==========

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        return ResponseEntity.ok(ApiResponse.success("OK", systemService.getSystemSettings()));
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody Map<String, String> settings) {
        systemService.saveSystemSettings(settings);
        return ResponseEntity.ok(ApiResponse.success("系统配置已保存"));
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestBody Map<String, String> body) {
        String newPwd = body.get("new_password");
        String confirmPwd = body.get("confirm_password");
        if (newPwd == null || newPwd.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(400, "密码不能为空"));
        }
        if (newPwd.length() < 6) {
            return ResponseEntity.ok(ApiResponse.error(400, "密码长度不能少于6位"));
        }
        if (newPwd.length() > 128) {
            return ResponseEntity.ok(ApiResponse.error(400, "密码长度不能超过128位"));
        }
        if (!newPwd.equals(confirmPwd)) {
            return ResponseEntity.ok(ApiResponse.error(400, "两次输入的密码不一致"));
        }
        authService.updatePassword(newPwd);
        return ResponseEntity.ok(ApiResponse.success("密码已更新"));
    }

    // ========== System Export/Import ==========

    @GetMapping("/system/export")
    public void exportSystem(HttpServletResponse response) throws IOException {
        Map<String, Object> data = systemService.exportAllData();
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=System_Migrate_" +
                new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".json");
        objectMapper.writeValue(response.getOutputStream(), data);
    }

    @PostMapping("/system/import")
    public ResponseEntity<?> importSystem(@RequestParam("file") MultipartFile file) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(file.getInputStream(), Map.class);
            systemService.importAllData(data);
            return ResponseEntity.ok(ApiResponse.success("完美迁移完成！系统数据已全部恢复。"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(500, "迁移导入失败"));
        }
    }

    // ========== Card Types ==========

    @GetMapping("/card-types")
    public ResponseEntity<?> getCardTypes() {
        return ResponseEntity.ok(ApiResponse.success("OK", CardService.getCardTypes()));
    }

    private static Integer toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return 0; }
    }

    private static String toStr(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
