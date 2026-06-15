package com.guyi.access.service;

import com.guyi.access.repository.*;
import org.springframework.stereotype.Service;

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
        long total = cardRepository.countByAppIdGreaterThan(0);
        long unused = cardRepository.countByStatusAndAppIdGreaterThan(0, 0);
        long used = cardRepository.countByStatusAndAppIdGreaterThan(1, 0);
        long active = activeDeviceRepository.countActiveDevices();

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
        stats.put("active", active);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        result.put("chart_types", chartTypes);
        result.put("app_stats", appStatsList);
        return result;
    }
}
