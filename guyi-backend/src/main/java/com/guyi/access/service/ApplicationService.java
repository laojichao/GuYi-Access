package com.guyi.access.service;

import com.guyi.access.entity.Application;
import com.guyi.access.entity.AppVariable;
import com.guyi.access.exception.BusinessException;
import com.guyi.access.repository.ApplicationRepository;
import com.guyi.access.repository.AppVariableRepository;
import com.guyi.access.repository.CardRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final AppVariableRepository appVariableRepository;
    private final CardRepository cardRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                              AppVariableRepository appVariableRepository,
                              CardRepository cardRepository) {
        this.applicationRepository = applicationRepository;
        this.appVariableRepository = appVariableRepository;
        this.cardRepository = cardRepository;
    }

    public List<Map<String, Object>> getAllApps() {
        List<Object[]> results = applicationRepository.findAllWithCardCount();
        return results.stream().map(row -> {
            Application app = (Application) row[0];
            Long cardCount = (Long) row[1];
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", app.getId());
            map.put("app_name", app.getAppName());
            map.put("app_key", app.getAppKey());
            map.put("app_version", app.getAppVersion());
            map.put("status", app.getStatus());
            map.put("create_time", app.getCreateTime());
            map.put("notes", app.getNotes());
            map.put("update_url", app.getUpdateUrl());
            map.put("force_update", app.getForceUpdate());
            map.put("card_count", cardCount);
            return map;
        }).collect(Collectors.toList());
    }

    public String createApp(String name, String version, String notes) {
        String appKey = generateAppKey();
        Application app = new Application();
        app.setAppName(name);
        app.setAppKey(appKey);
        app.setAppVersion(version);
        app.setNotes(notes);
        applicationRepository.save(app);
        return appKey;
    }

    @Transactional
    public void updateApp(Integer id, String name, String version, String notes, String updateUrl, Integer forceUpdate) {
        if (applicationRepository.existsByAppNameAndIdNot(name, id)) {
            throw new BusinessException("应用名称已存在");
        }
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        app.setAppName(name);
        app.setAppVersion(version);
        app.setNotes(notes);
        app.setUpdateUrl(updateUrl);
        app.setForceUpdate(forceUpdate);
        applicationRepository.save(app);
    }

    @Transactional
    public void toggleAppStatus(Integer id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("应用不存在"));
        app.setStatus(app.getStatus() == 1 ? 0 : 1);
        applicationRepository.save(app);
    }

    @Transactional
    public void deleteApp(Integer id) {
        long cardCount = cardRepository.countByAppId(id);
        if (cardCount > 0) {
            throw new BusinessException("无法删除：该应用下仍有 " + cardCount + " 张卡密。");
        }
        appVariableRepository.deleteByAppId(id);
        applicationRepository.deleteById(id);
    }

    public Application getAppByKey(String appKey) {
        return applicationRepository.findByAppKey(appKey).orElse(null);
    }

    // App Variables
    public List<AppVariable> getVariables(Integer appId, boolean onlyPublic) {
        if (onlyPublic) {
            return appVariableRepository.findByAppIdAndIsPublic(appId, 1);
        }
        return appVariableRepository.findByAppId(appId);
    }

    @Transactional
    public void addVariable(Integer appId, String key, String value, Integer isPublic) {
        if (appVariableRepository.findByAppIdAndKeyName(appId, key).isPresent()) {
            throw new BusinessException("变量名重复");
        }
        AppVariable var = new AppVariable();
        var.setAppId(appId);
        var.setKeyName(key);
        var.setValue(value);
        var.setIsPublic(isPublic);
        appVariableRepository.save(var);
    }

    @Transactional
    public void updateVariable(Integer id, String key, String value, Integer isPublic) {
        AppVariable var = appVariableRepository.findById(id)
                .orElseThrow(() -> new BusinessException("变量不存在"));
        if (appVariableRepository.existsByAppIdAndKeyNameAndIdNot(var.getAppId(), key, id)) {
            throw new BusinessException("变量名重复");
        }
        var.setKeyName(key);
        var.setValue(value);
        var.setIsPublic(isPublic);
        appVariableRepository.save(var);
    }

    @Transactional
    public void deleteVariable(Integer id) {
        appVariableRepository.deleteById(id);
    }

    private String generateAppKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytesToHex(bytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
