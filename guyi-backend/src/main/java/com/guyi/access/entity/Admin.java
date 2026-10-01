package com.guyi.access.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "admin")
public class Admin {

    @Id
    private Integer id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Revocation counter embedded in every issued JWT. Bumping it (logout, password change)
     * invalidates all tokens minted with an older value. Legacy rows may be NULL, read as 0.
     */
    @Column(name = "token_version")
    private Integer tokenVersion = 0;
}
