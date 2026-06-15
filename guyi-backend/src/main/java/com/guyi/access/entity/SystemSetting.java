package com.guyi.access.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @Column(name = "key_name", length = 50)
    private String keyName;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;
}
