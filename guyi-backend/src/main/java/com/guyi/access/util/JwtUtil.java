package com.guyi.access.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    /** HS256 needs at least 256 bits; a shorter key makes jjwt throw only at the first signing. */
    private static final int MIN_SECRET_BYTES = 32;

    /** Claim carrying the revocation version; see {@code Admin.tokenVersion}. */
    private static final String TOKEN_VERSION_CLAIM = "ver";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) 必须至少为 "
                    + MIN_SECRET_BYTES + " 字节（256 位），当前配置不满足要求，拒绝启动");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, int tokenVersion) {
        return Jwts.builder()
                .subject(username)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Revocation version the token was issued with. Tokens minted before revocation existed carry no
     * claim and are treated as version 0, matching the column default.
     */
    public Integer getTokenVersion(String token) {
        return parseToken(token).get(TOKEN_VERSION_CLAIM, Integer.class);
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }
}
