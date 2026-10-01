package com.guyi.access.service;

import com.guyi.access.repository.ActiveDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Housekeeping that used to be either missing or done on a random 1%-of-requests basis inside the
 * verify hot path.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final ActiveDeviceRepository activeDeviceRepository;
    private final CardService cardService;
    private final SystemService systemService;
    private final AuthService authService;

    @Value("${app.maintenance.enabled:true}")
    private boolean maintenanceEnabled;

    public MaintenanceService(ActiveDeviceRepository activeDeviceRepository,
                              CardService cardService,
                              SystemService systemService,
                              AuthService authService) {
        this.activeDeviceRepository = activeDeviceRepository;
        this.cardService = cardService;
        this.systemService = systemService;
        this.authService = authService;
    }

    /**
     * Marks expired device sessions inactive. Non-destructive (a status flag only), so it runs
     * unconditionally - the dashboard's "active" count depends on it.
     */
    @Scheduled(cron = "${app.maintenance.device-cleanup-cron:0 */10 * * * *}")
    @Transactional
    public void deactivateExpiredDevices() {
        if (!maintenanceEnabled) {
            return;
        }
        int count = activeDeviceRepository.deactivateExpiredDevices();
        if (count > 0) {
            log.info("已失活 {} 条过期设备会话", count);
        }
    }

    /**
     * Deletes expired cards - DESTRUCTIVE, therefore opt-in: it only runs while the system setting
     * {@code auto_clean_expired_cards} is exactly "1". The manual admin endpoint stays available
     * either way.
     */
    @Scheduled(cron = "${app.maintenance.expired-card-purge-cron:0 30 * * * *}")
    public void purgeExpiredCardsIfEnabled() {
        if (!maintenanceEnabled) {
            return;
        }
        String enabled = systemService.getSystemSettings().getOrDefault("auto_clean_expired_cards", "0");
        if (!"1".equals(enabled)) {
            return;
        }
        int count = cardService.cleanupExpiredCards();
        if (count > 0) {
            log.info("已按系统设置自动清理 {} 张过期卡密", count);
        }
    }

    /** The install endpoint is open until the first administrator exists - say so loudly at boot. */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfNotInstalled() {
        if (!authService.isInstalled()) {
            log.warn("系统尚未安装：POST /api/admin/install 当前对外开放，任何先到的访问者都可以创建管理员账号。"
                    + "请部署后立即完成安装（该接口在安装完成后自动关闭，且已纳入登录级限流）。");
        }
    }
}
