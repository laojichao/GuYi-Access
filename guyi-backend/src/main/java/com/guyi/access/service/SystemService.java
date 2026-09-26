package com.guyi.access.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guyi.access.entity.*;
import com.guyi.access.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.util.*;

@Service
public class SystemService {

    private final SystemSettingRepository systemSettingRepository;
    private final ApplicationRepository applicationRepository;
    private final AppVariableRepository appVariableRepository;
    private final CardRepository cardRepository;
    private final ActiveDeviceRepository activeDeviceRepository;
    private final UsageLogRepository usageLogRepository;
    private final BlacklistRepository blacklistRepository;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    public SystemService(SystemSettingRepository systemSettingRepository,
                         ApplicationRepository applicationRepository,
                         AppVariableRepository appVariableRepository,
                         CardRepository cardRepository,
                         ActiveDeviceRepository activeDeviceRepository,
                         UsageLogRepository usageLogRepository,
                         BlacklistRepository blacklistRepository,
                         AdminRepository adminRepository,
                         ObjectMapper objectMapper) {
        this.systemSettingRepository = systemSettingRepository;
        this.applicationRepository = applicationRepository;
        this.appVariableRepository = appVariableRepository;
        this.cardRepository = cardRepository;
        this.activeDeviceRepository = activeDeviceRepository;
        this.usageLogRepository = usageLogRepository;
        this.blacklistRepository = blacklistRepository;
        this.adminRepository = adminRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, String> getSystemSettings() {
        List<SystemSetting> settings = systemSettingRepository.findAll();
        Map<String, String> map = new LinkedHashMap<>();
        for (SystemSetting s : settings) {
            map.put(s.getKeyName(), s.getValue());
        }
        return map;
    }

    @Transactional
    public void saveSystemSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            SystemSetting setting = systemSettingRepository.findById(entry.getKey())
                    .orElse(new SystemSetting());
            setting.setKeyName(entry.getKey());
            setting.setValue(entry.getValue());
            systemSettingRepository.save(setting);
        }
    }

    @Transactional
    public Map<String, Object> exportAllData() {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("applications", applicationRepository.findAll());
        data.put("app_variables", appVariableRepository.findAll());
        data.put("cards", cardRepository.findAll());
        data.put("active_devices", activeDeviceRepository.findAll());
        data.put("usage_logs", usageLogRepository.findAll());
        data.put("blacklists", blacklistRepository.findAll());
        data.put("system_settings", systemSettingRepository.findAll());
        data.put("admin", adminRepository.findAll());

        return data;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void importAllData(Map<String, Object> data) {
        // Clear all tables
        usageLogRepository.deleteAll();
        activeDeviceRepository.deleteAll();
        cardRepository.deleteAll();
        appVariableRepository.deleteAll();
        applicationRepository.deleteAll();
        blacklistRepository.deleteAll();
        systemSettingRepository.deleteAll();

        // Import applications
        if (data.containsKey("applications")) {
            List<Map<String, Object>> apps = (List<Map<String, Object>>) data.get("applications");
            for (Map<String, Object> row : apps) {
                if (isBlank(getField(row, "appName", "app_name")) || isBlank(getField(row, "appKey", "app_key"))) {
                    continue; // Skip rows missing required fields instead of failing the whole import
                }
                Application app = new Application();
                app.setId(toInt(getField(row, "id")));
                app.setAppName(toStr(getField(row, "appName", "app_name")));
                app.setAppKey(toStr(getField(row, "appKey", "app_key")));
                app.setAppVersion(toStr(getField(row, "appVersion", "app_version")));
                app.setStatus(toInt(getField(row, "status")));
                app.setCreateTime(toDateTime(getField(row, "createTime", "create_time")));
                app.setNotes(toStr(getField(row, "notes")));
                app.setUpdateUrl(toStr(getField(row, "updateUrl", "update_url")));
                app.setForceUpdate(toInt(getField(row, "forceUpdate", "force_update")));
                applicationRepository.save(app);
            }
        }

        // Import app_variables
        if (data.containsKey("app_variables")) {
            List<Map<String, Object>> vars = (List<Map<String, Object>>) data.get("app_variables");
            for (Map<String, Object> row : vars) {
                AppVariable v = new AppVariable();
                v.setId(toInt(getField(row, "id")));
                v.setAppId(toInt(getField(row, "appId", "app_id")));
                v.setKeyName(toStr(getField(row, "keyName", "key_name")));
                v.setValue(toStr(getField(row, "value")));
                v.setIsPublic(toInt(getField(row, "isPublic", "is_public")));
                v.setCreateTime(toDateTime(getField(row, "createTime", "create_time")));
                appVariableRepository.save(v);
            }
        }

        // Import cards
        if (data.containsKey("cards")) {
            List<Map<String, Object>> cards = (List<Map<String, Object>>) data.get("cards");
            for (Map<String, Object> row : cards) {
                if (isBlank(getField(row, "cardCode", "card_code"))) {
                    continue;
                }
                Card card = new Card();
                card.setId(toInt(getField(row, "id")));
                card.setCardCode(toStr(getField(row, "cardCode", "card_code")));
                card.setCardType(toStr(getField(row, "cardType", "card_type")));
                card.setStatus(toInt(getField(row, "status")));
                card.setDeviceHash(toStr(getField(row, "deviceHash", "device_hash")));
                // Restore activation/expiry timestamps - losing these would invalidate every active card
                card.setUsedTime(toDateTime(getField(row, "usedTime", "used_time")));
                card.setExpireTime(toDateTime(getField(row, "expireTime", "expire_time")));
                card.setCreateTime(toDateTime(getField(row, "createTime", "create_time")));
                card.setNotes(toStr(getField(row, "notes")));
                card.setAppId(toInt(getField(row, "appId", "app_id")));
                card.setCustomData(toStr(getField(row, "customData", "custom_data")));
                card.setDuration(toInt(getField(row, "duration")));
                cardRepository.save(card);
            }
        }

        // Import blacklists
        if (data.containsKey("blacklists")) {
            List<Map<String, Object>> bls = (List<Map<String, Object>>) data.get("blacklists");
            for (Map<String, Object> row : bls) {
                Blacklist bl = new Blacklist();
                bl.setId(toInt(getField(row, "id")));
                bl.setType(toStr(getField(row, "type")));
                bl.setValue(toStr(getField(row, "value")));
                bl.setReason(toStr(getField(row, "reason")));
                blacklistRepository.save(bl);
            }
        }

        // Import system_settings
        if (data.containsKey("system_settings")) {
            List<Map<String, Object>> settings = (List<Map<String, Object>>) data.get("system_settings");
            for (Map<String, Object> row : settings) {
                SystemSetting s = new SystemSetting();
                s.setKeyName(toStr(getField(row, "keyName", "key_name")));
                s.setValue(toStr(getField(row, "value")));
                systemSettingRepository.save(s);
            }
        }

        // Import active_devices
        if (data.containsKey("active_devices")) {
            List<Map<String, Object>> devices = (List<Map<String, Object>>) data.get("active_devices");
            for (Map<String, Object> row : devices) {
                if (isBlank(getField(row, "deviceHash", "device_hash"))
                        || isBlank(getField(row, "cardCode", "card_code"))) {
                    continue;
                }
                java.time.LocalDateTime expireTime = toDateTime(getField(row, "expireTime", "expire_time"));
                if (expireTime == null) {
                    continue; // expire_time is NOT NULL - an active device without expiry is meaningless
                }
                ActiveDevice d = new ActiveDevice();
                d.setId(toInt(getField(row, "id")));
                d.setDeviceHash(toStr(getField(row, "deviceHash", "device_hash")));
                d.setCardCode(toStr(getField(row, "cardCode", "card_code")));
                d.setCardType(toStr(getField(row, "cardType", "card_type")));
                d.setActivateTime(toDateTime(getField(row, "activateTime", "activate_time")));
                d.setExpireTime(expireTime);
                d.setStatus(toInt(getField(row, "status")));
                d.setAppId(toInt(getField(row, "appId", "app_id")));
                activeDeviceRepository.save(d);
            }
        }

        // Import usage_logs
        if (data.containsKey("usage_logs")) {
            List<Map<String, Object>> logs = (List<Map<String, Object>>) data.get("usage_logs");
            for (Map<String, Object> row : logs) {
                if (isBlank(getField(row, "cardCode", "card_code"))) {
                    continue;
                }
                UsageLog log = new UsageLog();
                log.setId(toInt(getField(row, "id")));
                log.setCardCode(toStr(getField(row, "cardCode", "card_code")));
                log.setCardType(toStr(getField(row, "cardType", "card_type")));
                log.setDeviceHash(toStr(getField(row, "deviceHash", "device_hash")));
                log.setIpAddress(toStr(getField(row, "ipAddress", "ip_address")));
                log.setUserAgent(toStr(getField(row, "userAgent", "user_agent")));
                log.setAccessTime(toDateTime(getField(row, "accessTime", "access_time")));
                log.setResult(toStr(getField(row, "result")));
                log.setAppName(toStr(getField(row, "appName", "app_name")));
                usageLogRepository.save(log);
            }
        }

        // Import admin
        if (data.containsKey("admin")) {
            List<Map<String, Object>> admins = (List<Map<String, Object>>) data.get("admin");
            for (Map<String, Object> row : admins) {
                Admin admin = new Admin();
                admin.setId(toInt(getField(row, "id")));
                admin.setUsername(toStr(getField(row, "username")));
                admin.setPasswordHash(toStr(getField(row, "passwordHash", "password_hash")));
                adminRepository.save(admin);
            }
        }
    }

    private Integer toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return 0; }
    }

    private String toStr(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private boolean isBlank(Object obj) {
        return obj == null || obj.toString().trim().isEmpty();
    }

    /**
     * Parses datetime values from migration files. Handles ISO-8601 ("2026-01-01T12:00:00",
     * as produced by this system's export) and the legacy PHP format ("2026-01-01 12:00:00").
     */
    private java.time.LocalDateTime toDateTime(Object obj) {
        if (obj == null) return null;
        String s = obj.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return java.time.LocalDateTime.parse(s);
        } catch (java.time.format.DateTimeParseException ignored) {
        }
        try {
            return java.time.LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (java.time.format.DateTimeParseException ignored) {
        }
        return null;
    }

    private Object getField(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }
}
