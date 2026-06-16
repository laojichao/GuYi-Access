package com.guyi.access.service;

import com.guyi.access.entity.*;
import com.guyi.access.repository.*;
import com.guyi.access.util.CardCodeGenerator;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CardService {

    private static final Map<String, int[]> CARD_TYPES = Map.of(
            "hour", new int[]{3600},
            "day", new int[]{86400},
            "week", new int[]{604800},
            "month", new int[]{2592000},
            "season", new int[]{7776000},
            "year", new int[]{31536000}
    );

    private final CardRepository cardRepository;
    private final ActiveDeviceRepository activeDeviceRepository;
    private final ApplicationRepository applicationRepository;
    private final AppVariableRepository appVariableRepository;
    private final BlacklistRepository blacklistRepository;
    private final UsageLogRepository usageLogRepository;

    public CardService(CardRepository cardRepository,
                       ActiveDeviceRepository activeDeviceRepository,
                       ApplicationRepository applicationRepository,
                       AppVariableRepository appVariableRepository,
                       BlacklistRepository blacklistRepository,
                       UsageLogRepository usageLogRepository) {
        this.cardRepository = cardRepository;
        this.activeDeviceRepository = activeDeviceRepository;
        this.applicationRepository = applicationRepository;
        this.appVariableRepository = appVariableRepository;
        this.blacklistRepository = blacklistRepository;
        this.usageLogRepository = usageLogRepository;
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ========== Card Verification (core business logic from database.php verifyCard) ==========

    @Transactional
    public Map<String, Object> verifyCard(String cardCode, String deviceHash, String appKey, String customData,
                                           String clientIp, String userAgent) {
        // Cleanup expired devices randomly (1% chance)
        if (SECURE_RANDOM.nextInt(100) == 0) {
            activeDeviceRepository.deactivateExpiredDevices();
        }

        // Check blacklist
        Optional<Blacklist> blByIp = blacklistRepository.findByTypeAndValue("ip", clientIp);
        Optional<Blacklist> blByDevice = blacklistRepository.findByTypeAndValue("device", deviceHash);
        if (blByIp.isPresent() || blByDevice.isPresent()) {
            String reason = blByIp.map(Blacklist::getReason)
                    .or(() -> blByDevice.map(Blacklist::getReason))
                    .orElse("触发系统安全规则");
            if (reason.isEmpty()) reason = "触发系统安全规则";
            return Map.of("success", false, "message", "访问受限：设备或IP已被云端封禁 (" + reason + ")");
        }

        if (appKey == null || appKey.isEmpty()) {
            return Map.of("success", false, "message", "鉴权失败：未提供AppKey");
        }

        Application app = applicationRepository.findByAppKey(appKey).orElse(null);
        if (app == null) {
            return Map.of("success", false, "message", "应用密钥无效");
        }
        if (app.getStatus() == 0) {
            return Map.of("success", false, "message", "应用已被禁用");
        }

        Integer appId = app.getId();
        String appName = app.getAppName();

        // Check if device already has active session
        Optional<ActiveDevice> activeDevice = activeDeviceRepository
                .findByDeviceHashAndStatusAndExpireTimeAfterAndAppId(deviceHash, 1, LocalDateTime.now(), appId);

        if (activeDevice.isPresent()) {
            ActiveDevice ad = activeDevice.get();
            if (ad.getCardCode().equals(cardCode)) {
                Card card = cardRepository.findByCardCode(cardCode).orElse(null);
                if (card == null) {
                    activeDeviceRepository.deleteByCardCode(cardCode);
                    return Map.of("success", false, "message", "卡密已失效");
                }
                if (card.getStatus() == 2) {
                    return Map.of("success", false, "message", "此卡密已被管理员封禁");
                }

                if (customData != null && !customData.isEmpty()) {
                    card.setCustomData(customData);
                    cardRepository.save(card);
                }

                logUsage(cardCode, ad.getCardType(), deviceHash, clientIp, userAgent, "设备活跃", appName);
                return Map.of("success", true, "message", "设备已激活",
                        "expire_time", ad.getExpireTime().toString(),
                        "app_id", appId, "custom_data", card.getCustomData() != null ? card.getCustomData() : "");
            }
        }

        // Look up the card
        Card card = cardRepository.findByCardCodeAndAppId(cardCode, appId).orElse(null);
        if (card == null) {
            return Map.of("success", false, "message", "无效的卡密 (或不属于当前应用)");
        }
        if (card.getStatus() == 2) {
            return Map.of("success", false, "message", "此卡密已被管理员封禁");
        }

        if (card.getStatus() == 1) {
            // Already activated card
            if (card.getExpireTime().isBefore(LocalDateTime.now())) {
                return Map.of("success", false, "message", "卡密已过期");
            }
            if (card.getDeviceHash() != null && !card.getDeviceHash().isEmpty()
                    && !card.getDeviceHash().equals(deviceHash)) {
                return Map.of("success", false, "message", "卡密已绑定其他设备");
            }

            if (customData != null && !customData.isEmpty()) {
                card.setDeviceHash(deviceHash);
                card.setCustomData(customData);
            } else if (!deviceHash.equals(card.getDeviceHash())) {
                card.setDeviceHash(deviceHash);
            }
            cardRepository.save(card);

            // Upsert active device
            ActiveDevice newAd = new ActiveDevice();
            newAd.setDeviceHash(deviceHash);
            newAd.setCardCode(cardCode);
            newAd.setCardType(card.getCardType());
            newAd.setExpireTime(card.getExpireTime());
            newAd.setStatus(1);
            newAd.setAppId(appId);
            activeDeviceRepository.deleteByCardCode(cardCode);
            activeDeviceRepository.save(newAd);

            return Map.of("success", true, "message", "验证通过",
                    "expire_time", card.getExpireTime().toString(),
                    "app_id", appId,
                    "custom_data", card.getCustomData() != null ? card.getCustomData() : "");
        } else {
            // First activation (status == 0)
            int duration = card.getDuration() > 0 ? card.getDuration()
                    : CARD_TYPES.getOrDefault(card.getCardType(), new int[]{86400})[0];

            String newCustomData = (customData != null && !customData.isEmpty()) ? customData : card.getCustomData();

            card.setStatus(1);
            card.setDeviceHash(deviceHash);
            card.setUsedTime(LocalDateTime.now());
            card.setExpireTime(LocalDateTime.now().plusSeconds(duration));
            card.setCustomData(newCustomData);
            cardRepository.save(card);

            // Refresh to get computed expire_time
            card = cardRepository.findById(card.getId()).orElse(card);

            ActiveDevice newAd = new ActiveDevice();
            newAd.setDeviceHash(deviceHash);
            newAd.setCardCode(cardCode);
            newAd.setCardType(card.getCardType());
            newAd.setExpireTime(card.getExpireTime());
            newAd.setStatus(1);
            newAd.setAppId(appId);
            activeDeviceRepository.save(newAd);

            logUsage(cardCode, card.getCardType(), deviceHash, clientIp, userAgent, "激活成功", appName);
            return Map.of("success", true, "message", "首次激活成功",
                    "expire_time", card.getExpireTime().toString(),
                    "app_id", appId,
                    "custom_data", newCustomData != null ? newCustomData : "");
        }
    }

    // ========== Card Generation ==========

    @Transactional
    public List<String> generateCards(int count, String type, String prefix, String note,
                                       Integer appId, int customDuration) {
        if (appId == null || appId <= 0) {
            throw new RuntimeException("必须指定有效的应用 ID");
        }

        List<Card> cards = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = (prefix != null ? prefix : "") + CardCodeGenerator.generate(16);
            Card card = new Card();
            card.setCardCode(code);
            card.setCardType(type);
            card.setNotes(note);
            card.setAppId(appId);
            card.setDuration(customDuration);
            cards.add(card);
            codes.add(code);
        }
        cardRepository.saveAll(cards);
        return codes;
    }

    // ========== Batch Operations ==========

    @Transactional
    public int batchDeleteCards(List<Integer> ids) {
        if (ids.isEmpty()) return 0;
        List<Card> cards = cardRepository.findByIdIn(ids);
        List<String> codes = cards.stream().map(Card::getCardCode).collect(Collectors.toList());
        if (!codes.isEmpty()) {
            activeDeviceRepository.deleteByCardCodeIn(codes);
        }
        return cardRepository.deleteByIdIn(ids);
    }

    @Transactional
    public int batchUnbindCards(List<Integer> ids) {
        if (ids.isEmpty()) return 0;
        List<Card> cards = cardRepository.findByIdIn(ids);
        List<String> codes = cards.stream().map(Card::getCardCode).collect(Collectors.toList());
        if (!codes.isEmpty()) {
            activeDeviceRepository.deleteByCardCodeIn(codes);
        }
        return cardRepository.unbindByIds(ids);
    }

    @Transactional
    public int batchAddTime(List<Integer> ids, double hours) {
        if (ids.isEmpty() || hours <= 0) return 0;
        long seconds = (long) (hours * 3600);
        List<Card> cards = cardRepository.findByIdIn(ids);
        List<String> codes = cards.stream()
                .filter(c -> c.getStatus() == 1)
                .map(Card::getCardCode)
                .collect(Collectors.toList());
        if (!codes.isEmpty()) {
            activeDeviceRepository.addTimeByCardCodes(codes, seconds);
        }
        return cardRepository.addTimeByIds(ids, seconds);
    }

    @Transactional
    public int batchSubTime(List<Integer> ids, double hours) {
        if (ids.isEmpty() || hours <= 0) return 0;
        long seconds = (long) (hours * 3600);
        List<Card> cards = cardRepository.findByIdIn(ids);
        List<String> codes = cards.stream()
                .filter(c -> c.getStatus() == 1)
                .map(Card::getCardCode)
                .collect(Collectors.toList());
        if (!codes.isEmpty()) {
            activeDeviceRepository.subTimeByCardCodes(codes, seconds);
        }
        return cardRepository.subTimeByIds(ids, seconds);
    }

    @Transactional
    public void globalCompensate(double hours, Integer appId) {
        long seconds = (long) (hours * 3600);
        cardRepository.globalAddTime(seconds, appId);
        activeDeviceRepository.globalAddTime(seconds, appId);
    }

    // ========== Card Status ==========

    @Transactional
    public void updateCardStatus(Integer id, Integer status) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("卡密不存在"));
        if (status == 1 && (card.getExpireTime() == null)) {
            status = 0;
        }
        card.setStatus(status);
        cardRepository.save(card);

        if (status == 2) {
            activeDeviceRepository.deleteByCardCode(card.getCardCode());
        }
    }

    public void deleteCard(Integer id) {
        batchDeleteCards(List.of(id));
    }

    public void resetDeviceBinding(Integer id) {
        batchUnbindCards(List.of(id));
    }

    // ========== Unbind via API (deducts 12 hours) ==========

    @Transactional
    public boolean unbindCardByApi(String cardCode) {
        Card card = cardRepository.findByCardCode(cardCode).orElse(null);
        if (card == null || card.getStatus() != 1) return false;

        card.setDeviceHash(null);
        card.setExpireTime(card.getExpireTime().minusHours(12));
        cardRepository.save(card);
        activeDeviceRepository.deleteByCardCode(cardCode);
        return true;
    }

    // ========== Cleanup ==========

    @Transactional
    public int cleanupExpiredCards() {
        List<Card> expired = cardRepository.findExpiredCards(LocalDateTime.now());
        if (expired.isEmpty()) return 0;

        List<String> codes = expired.stream().map(Card::getCardCode).collect(Collectors.toList());
        List<Integer> ids = expired.stream().map(Card::getId).collect(Collectors.toList());

        activeDeviceRepository.deleteByCardCodeIn(codes);
        cardRepository.deleteByIdIn(ids);
        return ids.size();
    }

    // ========== Search & Pagination ==========

    public Page<Card> getCardsPaginated(int page, int size, Integer status, Integer appId,
                                         String cardType, String sort) {
        Sort sortObj;
        if ("expire_asc".equals(sort)) {
            sortObj = Sort.by(Sort.Direction.ASC, "expireTime");
        } else if ("expire_desc".equals(sort)) {
            sortObj = Sort.by(Sort.Direction.DESC, "expireTime");
        } else {
            sortObj = Sort.by(Sort.Direction.DESC, "createTime");
        }

        Pageable pageable = PageRequest.of(page, size, sortObj);

        if (status != null && appId != null && cardType != null) {
            return cardRepository.findByStatusAndAppIdAndCardType(status, appId, cardType, pageable);
        } else if (status != null && appId != null) {
            return cardRepository.findByStatusAndAppId(status, appId, pageable);
        } else if (appId != null && cardType != null) {
            return cardRepository.findByAppIdAndCardType(appId, cardType, pageable);
        } else if (status != null) {
            return cardRepository.findByStatus(status, pageable);
        } else if (appId != null) {
            return cardRepository.findByAppId(appId, pageable);
        } else {
            return cardRepository.findAll(pageable);
        }
    }

    public List<Card> searchCards(String keyword) {
        return cardRepository.searchByKeyword(keyword);
    }

    public Page<Card> searchCardsPaged(String keyword, Pageable pageable) {
        return cardRepository.searchByKeywordPaged(keyword, pageable);
    }

    public Optional<Card> getCardByCode(String cardCode) {
        return cardRepository.findByCardCode(cardCode);
    }

    public List<Card> getCardsByIds(List<Integer> ids) {
        return cardRepository.findByIdIn(ids);
    }

    // ========== Card Types ==========

    public static Map<String, Object> getCardTypes() {
        Map<String, Object> types = new LinkedHashMap<>();
        types.put("hour", Map.of("name", "小时卡", "duration", 3600));
        types.put("day", Map.of("name", "天卡", "duration", 86400));
        types.put("week", Map.of("name", "周卡", "duration", 604800));
        types.put("month", Map.of("name", "月卡", "duration", 2592000));
        types.put("season", Map.of("name", "季卡", "duration", 7776000));
        types.put("year", Map.of("name", "年卡", "duration", 31536000));
        return types;
    }

    private void logUsage(String cardCode, String cardType, String deviceHash,
                           String ip, String ua, String result, String appName) {
        UsageLog log = new UsageLog();
        log.setCardCode(cardCode);
        log.setCardType(cardType);
        log.setDeviceHash(deviceHash);
        log.setIpAddress(ip);
        log.setUserAgent(ua);
        log.setResult(result);
        log.setAppName(appName);
        usageLogRepository.save(log);
    }
}
