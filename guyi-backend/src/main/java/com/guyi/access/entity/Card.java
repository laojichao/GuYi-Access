package com.guyi.access.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cards", indexes = {
    @Index(name = "idx_card_app", columnList = "app_id"),
    @Index(name = "idx_card_hash", columnList = "device_hash"),
    @Index(name = "idx_card_status_expire", columnList = "status,expire_time")
})
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "card_code", nullable = false, unique = true, length = 50)
    private String cardCode;

    @Column(name = "card_type", nullable = false, length = 20)
    private String cardType;

    @Column(name = "status")
    private Integer status = 0;  // 0=unused, 1=active, 2=banned

    @Column(name = "device_hash", length = 100)
    private String deviceHash;

    @Column(name = "used_time")
    private LocalDateTime usedTime;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "app_id")
    private Integer appId = 0;

    @Column(name = "custom_data", columnDefinition = "TEXT")
    private String customData;

    @Column(name = "duration")
    private Integer duration = 0;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }
}
