package com.guyi.access.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "app_name", nullable = false, unique = true, length = 100)
    private String appName;

    @Column(name = "app_key", nullable = false, unique = true, length = 64)
    private String appKey;

    @Column(name = "app_version", length = 32)
    private String appVersion = "";

    @Column(name = "status")
    private Integer status = 1;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "update_url", length = 255)
    private String updateUrl = "";

    @Column(name = "force_update")
    private Integer forceUpdate = 0;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }
}
