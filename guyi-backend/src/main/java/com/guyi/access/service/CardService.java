package com.guyi.access.service;

import com.guyi.access.entity.*;
import com.guyi.access.exception.BusinessException;
import com.guyi.access.repository.*;
import com.guyi.access.util.CardCodeGenerator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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

    /** card_code is varchar(50) and a generated code is 16 chars; keep a safety margin. */
    private static final int MAX_CARD_PREFIX_LENGTH = 32;

    private final CardRepository cardRepository;
    private final ActiveDeviceRepository activeDeviceRepository;
    private final AppVariableRepository appVariableRepository;
    private final BlacklistRepository blacklistRepository;
    private final UsageLogRepository usageLogRepository;

    public CardService(CardRepository cardRepository,
                       ActiveDeviceRepository activeDeviceRepository,
                       AppVariableRepository appVariableRepository,
                       BlacklistRepository blacklistRepository,
                       UsageLogRepository usageLogRepository) {
        this.cardRepository = cardRepository;
        this.activeDeviceRepository = activeDeviceRepository;
        this.appVariableRepository = appVariableRepository;
        this.blacklistRepository = blacklistRepository;
        this.usageLogRepository = usageLogRepository;
    }

    // ========== Card Verification (core business logic from database.php verifyCard) ==========

    /**
     * Core verification. Accepts a pre-loaded application so callers that already resolved
     * the app (e.g. the verify endpoint, which needs it for update-info) skip a redundant query.
     */
    @Transactional
    public Map<String, Object> verifyCard(String cardCode, String deviceHash, Application app, String customData,
                                           String clientIp, String userAgent) {
        // Expired device sessions are now deactivated by MaintenanceService on a schedule instead of
        // randomly inside this hot path (which also wrote during otherwise read-only verifications).

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

        if (app == null) {
            return Map.of("success", false, "message", "鉴权失败：未提供AppKey");
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

        // Look up the card with a row lock so concurrent verifications of the same card
        // (e.g. first activation from two devices) are serialized
        Card card = cardRepository.findByCardCodeAndAppIdForUpdate(cardCode, appId).orElse(null);
        if (card == null) {
            return Map.of("success", false, "message", "无效的卡密 (或不属于当前应用)");
        }
        if (card.getStatus() == 2) {
            return Map.of("success", false, "message", "此卡密已被管理员封禁");
        }

        if (card.getStatus() == 1) {
            // Already activated card
            if (card.getExpireTime() == null || card.getExpireTime().isBefore(LocalDateTime.now())) {
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

            logUsage(cardCode, card.getCardType(), deviceHash, clientIp, userAgent, "验证通过", appName);

            return Map.of("success", true, "message", "验证通过",
                    "expire_time", card.getExpireTime().toString(),
                    "app_id", appId,
                    "custom_data", card.getCustomData() != null ? card.getCustomData() : "");
        } else if (card.getStatus() != 0) {
            return Map.of("success", false, "message", "卡密状态异常，请联系管理员");
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
            throw new BusinessException("必须指定有效的应用 ID");
        }
        if (count <= 0) {
            throw new BusinessException("生成数量必须大于 0");
        }
        if (type == null || type.isEmpty()) {
            type = "day";
        }
        // An unknown type would be stored as-is and later silently fall back to a 1-day card at
        // activation, so reject it here instead (custom cards carry their own duration).
        if (customDuration <= 0 && !CARD_TYPES.containsKey(type)) {
            throw new BusinessException("无效的卡密类型：" + type + "，可选值：" + String.join("/", CARD_TYPES.keySet()));
        }
        if (prefix != null && prefix.length() > MAX_CARD_PREFIX_LENGTH) {
            throw new BusinessException("卡密前缀不能超过 " + MAX_CARD_PREFIX_LENGTH + " 个字符");
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
        if (ids == null || ids.isEmpty()) return 0;
        List<Card> cards = cardRepository.findByIdIn(ids);
        List<String> codes = cards.stream().map(Card::getCardCode).collect(Collectors.toList());
        if (!codes.isEmpty()) {
            activeDeviceRepository.deleteByCardCodeIn(codes);
        }
        return cardRepository.deleteByIdIn(ids);
    }

    @Transactional
    public int batchUnbindCards(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Card> cards = cardRepository.findByIdIn(ids);
        List<String> codes = cards.stream().map(Card::getCardCode).collect(Collectors.toList());
        if (!codes.isEmpty()) {
            activeDeviceRepository.deleteByCardCodeIn(codes);
        }
        return cardRepository.unbindByIds(ids);
    }

    @Transactional
    public int batchAddTime(List<Integer> ids, Double hours) {
        if (ids == null || ids.isEmpty() || hours == null || hours <= 0) return 0;
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
    public int batchSubTime(List<Integer> ids, Double hours) {
        if (ids == null || ids.isEmpty() || hours == null || hours <= 0) return 0;
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
                .orElseThrow(() -> new BusinessException("卡密不存在"));
        if (status == 1 && (card.getExpireTime() == null)) {
            status = 0;
        }
        card.setStatus(status);
        cardRepository.save(card);

        if (status == 2) {
            activeDeviceRepository.deleteByCardCode(card.getCardCode());
        }
    }

    // @Transactional is required on the entry points themselves: an internal this. call would
    // bypass the Spring proxy, splitting the device cleanup and the card update into two
    // independent transactions and leaving inconsistent state when the second one fails.
    @Transactional
    public void deleteCard(Integer id) {
        batchDeleteCards(List.of(id));
    }

    @Transactional
    public void resetDeviceBinding(Integer id) {
        batchUnbindCards(List.of(id));
    }

    // ========== Unbind via API (deducts 12 hours) ==========

    @Transactional
    public boolean unbindCardByApi(String cardCode) {
        Card card = cardRepository.findByCardCode(cardCode).orElse(null);
        if (card == null || card.getStatus() != 1) return false;

        card.setDeviceHash(null);
        if (card.getExpireTime() != null) {
            card.setExpireTime(card.getExpireTime().minusHours(12));
        }
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

    /**
     * True when {@code deviceHash} provably belongs to the caller of this app: it has a live session
     * there, or the supplied card is currently bound to it. Used to stop anonymous callers from
     * blacklisting arbitrary device hashes through the ban_machine action.
     */
    @Transactional(readOnly = true)
    public boolean isDeviceOwnedByApp(String deviceHash, Integer appId, String cardCode) {
        if (deviceHash == null || deviceHash.isEmpty() || appId == null) {
            return false;
        }
        if (activeDeviceRepository
                .findByDeviceHashAndStatusAndExpireTimeAfterAndAppId(deviceHash, 1, LocalDateTime.now(), appId)
                .isPresent()) {
            return true;
        }
        if (cardCode != null && !cardCode.isEmpty()) {
            Card card = cardRepository.findByCardCodeAndAppId(cardCode, appId).orElse(null);
            return card != null && deviceHash.equals(card.getDeviceHash());
        }
        return false;
    }

    // ========== Search & Pagination ==========

    /** Whitelisted sort mapping shared by the list view and the keyword-search listing. */
    public static Sort buildSort(String sort) {
        if ("expire_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "expireTime");
        }
        if ("expire_desc".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "expireTime");
        }
        return Sort.by(Sort.Direction.DESC, "createTime");
    }

    public Page<Card> getCardsPaginated(int page, int size, Integer status, Integer appId,
                                         String cardType, String sort) {
        Sort sortObj = buildSort(sort);

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
        return cardRepository.searchByKeyword(toLikePattern(keyword));
    }

    public Page<Card> searchCardsPaged(String keyword, Integer status, Integer appId, String cardType,
                                       Pageable pageable) {
        return cardRepository.searchByKeywordPaged(toLikePattern(keyword), status, appId, cardType, pageable);
    }

    /**
     * Wraps the keyword for a LIKE query and escapes %, _ and the escape character itself,
     * so user input cannot inject wildcards (e.g. "%" alone would match every row).
     */
    private static String toLikePattern(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return "%";
        StringBuilder sb = new StringBuilder(kw.length() + 8).append('%');
        for (int i = 0; i < kw.length(); i++) {
            char ch = kw.charAt(i);
            if (ch == '!' || ch == '%' || ch == '_') sb.append('!');
            sb.append(ch);
        }
        return sb.append('%').toString();
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
