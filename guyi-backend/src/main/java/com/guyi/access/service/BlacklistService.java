package com.guyi.access.service;

import com.guyi.access.entity.Blacklist;
import com.guyi.access.exception.BusinessException;
import com.guyi.access.repository.BlacklistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BlacklistService {

    private final BlacklistRepository blacklistRepository;

    public BlacklistService(BlacklistRepository blacklistRepository) {
        this.blacklistRepository = blacklistRepository;
    }

    public List<Blacklist> getAll() {
        return blacklistRepository.findAllByOrderByCreateTimeDesc();
    }

    /**
     * @return true when a new row was written, false when this value was already blacklisted.
     */
    @Transactional
    public boolean addBlacklist(String type, String value, String reason) {
        if (type == null || (!"device".equals(type) && !"ip".equals(type))) {
            throw new BusinessException("黑名单类型无效（仅支持 device 或 ip）");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException("黑名单值不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("黑名单值不能超过 100 个字符");
        }
        // De-duplicate on the value alone, matching the table's unique constraint on (value):
        // checking (type, value) let a second row pass the check and still fail on INSERT.
        if (blacklistRepository.findByValue(normalized).isPresent()) {
            return false;
        }
        Blacklist bl = new Blacklist();
        bl.setType(type);
        bl.setValue(normalized);
        bl.setReason(reason);
        blacklistRepository.save(bl);
        return true;
    }

    @Transactional
    public void deleteBlacklist(Integer id) {
        blacklistRepository.deleteById(id);
    }

    @Transactional
    public void addDeviceAndIpBlacklist(String deviceHash, String ip, String reason) {
        if (deviceHash != null && !deviceHash.trim().isEmpty()) {
            addBlacklist("device", deviceHash, reason);
        }
        if (ip != null && !ip.trim().isEmpty()) {
            addBlacklist("ip", ip, reason);
        }
    }
}
