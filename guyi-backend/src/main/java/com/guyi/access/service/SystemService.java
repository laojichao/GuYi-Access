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
                Application app = new Application();
                app.setId(toInt(row.get("id")));
                app.setAppName(toStr(row.get("app_name")));
                app.setAppKey(toStr(row.get("app_key")));
                app.setAppVersion(toStr(row.get("app_version")));
                app.setStatus(toInt(row.get("status")));
                app.setNotes(toStr(row.get("notes")));
                app.setUpdateUrl(toStr(row.get("update_url")));
                app.setForceUpdate(toInt(row.get("force_update")));
                applicationRepository.save(app);
            }
        }

        // Import app_variables
        if (data.containsKey("app_variables")) {
            List<Map<String, Object>> vars = (List<Map<String, Object>>) data.get("app_variables");
            for (Map<String, Object> row : vars) {
                AppVariable v = new AppVariable();
                v.setId(toInt(row.get("id")));
                v.setAppId(toInt(row.get("app_id")));
                v.setKeyName(toStr(row.get("key_name")));
                v.setValue(toStr(row.get("value")));
                v.setIsPublic(toInt(row.get("is_public")));
                appVariableRepository.save(v);
            }
        }

        // Import cards
        if (data.containsKey("cards")) {
            List<Map<String, Object>> cards = (List<Map<String, Object>>) data.get("cards");
            for (Map<String, Object> row : cards) {
                Card card = new Card();
                card.setId(toInt(row.get("id")));
                card.setCardCode(toStr(row.get("card_code")));
                card.setCardType(toStr(row.get("card_type")));
                card.setStatus(toInt(row.get("status")));
                card.setDeviceHash(toStr(row.get("device_hash")));
                card.setNotes(toStr(row.get("notes")));
                card.setAppId(toInt(row.get("app_id")));
                card.setCustomData(toStr(row.get("custom_data")));
                card.setDuration(toInt(row.get("duration")));
                cardRepository.save(card);
            }
        }

        // Import blacklists
        if (data.containsKey("blacklists")) {
            List<Map<String, Object>> bls = (List<Map<String, Object>>) data.get("blacklists");
            for (Map<String, Object> row : bls) {
                Blacklist bl = new Blacklist();
                bl.setId(toInt(row.get("id")));
                bl.setType(toStr(row.get("type")));
                bl.setValue(toStr(row.get("value")));
                bl.setReason(toStr(row.get("reason")));
                blacklistRepository.save(bl);
            }
        }

        // Import system_settings
        if (data.containsKey("system_settings")) {
            List<Map<String, Object>> settings = (List<Map<String, Object>>) data.get("system_settings");
            for (Map<String, Object> row : settings) {
                SystemSetting s = new SystemSetting();
                s.setKeyName(toStr(row.get("key_name")));
                s.setValue(toStr(row.get("value")));
                systemSettingRepository.save(s);
            }
        }

        // Import active_devices
        if (data.containsKey("active_devices")) {
            List<Map<String, Object>> devices = (List<Map<String, Object>>) data.get("active_devices");
            for (Map<String, Object> row : devices) {
                ActiveDevice d = new ActiveDevice();
                d.setId(toInt(row.get("id")));
                d.setDeviceHash(toStr(row.get("device_hash")));
                d.setCardCode(toStr(row.get("card_code")));
                d.setCardType(toStr(row.get("card_type")));
                d.setStatus(toInt(row.get("status")));
                d.setAppId(toInt(row.get("app_id")));
                activeDeviceRepository.save(d);
            }
        }

        // Import usage_logs
        if (data.containsKey("usage_logs")) {
            List<Map<String, Object>> logs = (List<Map<String, Object>>) data.get("usage_logs");
            for (Map<String, Object> row : logs) {
                UsageLog log = new UsageLog();
                log.setId(toInt(row.get("id")));
                log.setCardCode(toStr(row.get("card_code")));
                log.setCardType(toStr(row.get("card_type")));
                log.setDeviceHash(toStr(row.get("device_hash")));
                log.setIpAddress(toStr(row.get("ip_address")));
                log.setUserAgent(toStr(row.get("user_agent")));
                log.setResult(toStr(row.get("result")));
                log.setAppName(toStr(row.get("app_name")));
                usageLogRepository.save(log);
            }
        }

        // Import admin
        if (data.containsKey("admin")) {
            List<Map<String, Object>> admins = (List<Map<String, Object>>) data.get("admin");
            for (Map<String, Object> row : admins) {
                Admin admin = new Admin();
                admin.setId(toInt(row.get("id")));
                admin.setUsername(toStr(row.get("username")));
                admin.setPasswordHash(toStr(row.get("password_hash")));
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
}
