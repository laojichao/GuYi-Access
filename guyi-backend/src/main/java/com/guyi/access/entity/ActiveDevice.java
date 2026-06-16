package com.guyi.access.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "active_devices", indexes = {
    @Index(name = "idx_dev_hash", columnList = "device_hash"),
    @Index(name = "idx_dev_expire", columnList = "expire_time"),
    @Index(name = "idx_dev_verify", columnList = "device_hash,status,app_id,expire_time")
})
public class ActiveDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "device_hash", nullable = false, length = 100)
    private String deviceHash;

    @Column(name = "card_code", nullable = false, unique = true, length = 50)
    private String cardCode;

    @Column(name = "card_type", nullable = false, length = 20)
    private String cardType;

    @Column(name = "activate_time", updatable = false)
    private LocalDateTime activateTime;

    @Column(name = "expire_time", nullable = false)
    private LocalDateTime expireTime;

    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "app_id")
    private Integer appId = 0;

    @PrePersist
    protected void onCreate() {
        this.activateTime = LocalDateTime.now();
    }
}
