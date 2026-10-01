package com.guyi.access.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single place that decides what "the client IP" is, shared by the rate limiter and the verify
 * endpoint so the two can never disagree.
 *
 * <p>Proxy headers are only honoured when {@code app.trust-proxy} is enabled, and a value is
 * accepted only if it is a literal IPv4/IPv6 address: an arbitrary attacker-supplied string must
 * never end up in a rate-limit key or a blacklist row.
 *
 * <p>With {@code trust-proxy} on, the LAST X-Forwarded-For entry is used - the address the nearest
 * trusted proxy itself observed. Earlier entries are client-supplied and can be rotated per request
 * to bypass rate limiting (a reverse proxy such as nginx appends the real client IP at the end).
 */
public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    public static String resolve(HttpServletRequest request, boolean trustProxy) {
        if (trustProxy) {
            String forwarded = lastForwardedFor(request.getHeader("X-Forwarded-For"));
            if (forwarded != null) {
                return forwarded;
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null) {
                String trimmed = realIp.trim();
                if (isIpLiteral(trimmed)) {
                    return trimmed;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private static String lastForwardedFor(String header) {
        if (header == null) {
            return null;
        }
        String[] parts = header.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if ("unknown".equalsIgnoreCase(candidate)) {
                continue;
            }
            if (isIpLiteral(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static boolean isIpLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            // IPv6 (a zone id may follow a '%')
            return value.matches("[0-9A-Fa-f:.%]+");
        }
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }
}
