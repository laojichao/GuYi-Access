package com.guyi.access.service;

import com.guyi.access.entity.Blacklist;
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

    @Transactional
    public void addBlacklist(String type, String value, String reason) {
        if (blacklistRepository.findByTypeAndValue(type, value).isPresent()) {
            return; // Already exists
        }
        Blacklist bl = new Blacklist();
        bl.setType(type);
        bl.setValue(value);
        bl.setReason(reason);
        blacklistRepository.save(bl);
    }

    @Transactional
    public void deleteBlacklist(Integer id) {
        blacklistRepository.deleteById(id);
    }

    @Transactional
    public void addDeviceAndIpBlacklist(String deviceHash, String ip, String reason) {
        addBlacklist("device", deviceHash, reason);
        addBlacklist("ip", ip, reason);
    }
}
