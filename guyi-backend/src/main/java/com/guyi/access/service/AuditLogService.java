package com.guyi.access.service;

import com.guyi.access.entity.UsageLog;
import com.guyi.access.repository.UsageLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Read access to the audit log (design Task 4). Card verification writes its entries through
 * {@code CardService.logUsage}; this service owns the query side so controllers never touch the
 * repository directly.
 */
@Service
public class AuditLogService {

    private final UsageLogRepository usageLogRepository;

    public AuditLogService(UsageLogRepository usageLogRepository) {
        this.usageLogRepository = usageLogRepository;
    }

    /** Newest first; {@code page} is 0-based and {@code limit} is already clamped by the caller. */
    public Page<UsageLog> getLogs(int page, int limit) {
        return usageLogRepository.findAllByOrderByAccessTimeDesc(PageRequest.of(page, limit));
    }
}
