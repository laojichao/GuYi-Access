package com.guyi.access.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "app_variables", indexes = {
    @Index(name = "idx_app_var", columnList = "app_id,key_name")
})
public class AppVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "app_id", nullable = false)
    private Integer appId;

    @Column(name = "key_name", nullable = false, length = 50)
    private String keyName;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "is_public")
    private Integer isPublic = 0;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
    }
}
