package com.guyi.access.repository;

import com.guyi.access.entity.UsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageLogRepository extends JpaRepository<UsageLog, Integer> {

    Page<UsageLog> findAllByOrderByAccessTimeDesc(Pageable pageable);
}
