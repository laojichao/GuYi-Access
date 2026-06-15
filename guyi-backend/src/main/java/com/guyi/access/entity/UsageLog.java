package com.guyi.access.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "usage_logs", indexes = {
    @Index(name = "idx_log_time", columnList = "access_time")
})
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "card_code", nullable = false, length = 50)
    private String cardCode;

    @Column(name = "card_type", nullable = false, length = 20)
    private String cardType;

    @Column(name = "device_hash", nullable = false, length = 100)
    private String deviceHash;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "access_time", updatable = false)
    private LocalDateTime accessTime;

    @Column(name = "result", length = 100)
    private String result;

    @Column(name = "app_name", length = 100)
    private String appName = "System";

    @PrePersist
    protected void onCreate() {
        this.accessTime = LocalDateTime.now();
    }
}
