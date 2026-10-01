package com.guyi.access.service;

import com.guyi.access.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DashboardService {

    private final CardRepository cardRepository;
    private final ActiveDeviceRepository activeDeviceRepository;
    private final ApplicationRepository applicationRepository;
    private final UsageLogRepository usageLogRepository;

    public DashboardService(CardRepository cardRepository,
                            ActiveDeviceRepository activeDeviceRepository,
                            ApplicationRepository applicationRepository,
                            UsageLogRepository usageLogRepository) {
        this.cardRepository = cardRepository;
        this.activeDeviceRepository = activeDeviceRepository;
        this.applicationRepository = applicationRepository;
        this.usageLogRepository = usageLogRepository;
    }

    public Map<String, Object> getDashboardData() {
        // Metric definitions, kept explicit so the UI and the API cannot drift apart:
        //   total   - every card that belongs to an application
        //   unused  - status 0, never activated
        //   used    - status 1, activated (includes cards whose expiry has already passed)
        //   banned  - status 2, blocked by an administrator
        //   expired - activated but past its expiry time
        //   active  - device sessions still live (maintained by MaintenanceService)
        //   apps    - number of applications
        long total = cardRepository.countByAppIdGreaterThan(0);
        long unused = cardRepository.countByStatusAndAppIdGreaterThan(0, 0);
        long used = cardRepository.countByStatusAndAppIdGreaterThan(1, 0);
        long banned = cardRepository.countByStatusAndAppIdGreaterThan(2, 0);
        long expired = cardRepository.countByStatusAndExpireTimeBefore(1, LocalDateTime.now());
        long active = activeDeviceRepository.countActiveDevices();
        long apps = applicationRepository.count();

        // Card type distribution
        List<Object[]> typeStats = cardRepository.countGroupByCardType();
        Map<String, Long> chartTypes = new LinkedHashMap<>();
        for (Object[] row : typeStats) {
            chartTypes.put((String) row[0], (Long) row[1]);
        }

        // App distribution
        List<Object[]> appStats = cardRepository.countGroupByApp();
        List<Map<String, Object>> appStatsList = new ArrayList<>();
        for (Object[] row : appStats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("app_name", row[0]);
            item.put("count", row[1]);
            appStatsList.add(item);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("unused", unused);
        stats.put("used", used);
        stats.put("banned", banned);
        stats.put("expired", expired);
        stats.put("active", active);
        stats.put("apps", apps);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        result.put("chart_types", chartTypes);
        result.put("app_stats", appStatsList);
        return result;
    }
}
