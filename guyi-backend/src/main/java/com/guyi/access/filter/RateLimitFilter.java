package com.guyi.access.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter implements Filter {

    @Value("${app.rate-limit.max-requests:60}")
    private int maxRequests;

    @Value("${app.rate-limit.login-max-requests:5}")
    private int loginMaxRequests;

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    private final Map<String, RateEntry> rateMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("HHmm");
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;
        String path = httpReq.getRequestURI();

        boolean isLogin = "/api/auth/login".equals(path);
        boolean isVerify = "/api/verify".equals(path);
        if (isLogin || isVerify) {
            String clientIp = getClientIp(httpReq);
            String currentMinute = MINUTE_FMT.format(LocalDateTime.now());
            int limit = isLogin ? loginMaxRequests : maxRequests;

            RateEntry entry = rateMap.compute(clientIp, (key, existing) -> {
                if (existing == null || !existing.minute.equals(currentMinute)) {
                    return new RateEntry(currentMinute, new AtomicInteger(1));
                }
                existing.count.incrementAndGet();
                return existing;
            });

            if (entry.count.get() > limit) {
                httpRes.setStatus(429);
                httpRes.setContentType("application/json;charset=UTF-8");
                httpRes.getWriter().write(objectMapper.writeValueAsString(
                        Map.of("code", 429, "msg", "Too Many Requests - 请稍后重试")));
                return;
            }

            // Periodic cleanup: evict stale entries every 5 minutes
            long now = System.currentTimeMillis();
            long last = lastCleanup.get();
            if (now - last > 300_000 && lastCleanup.compareAndSet(last, now)) {
                cleanupStaleEntries(currentMinute);
            }
        }

        chain.doFilter(request, response);
    }

    private void cleanupStaleEntries(String currentMinute) {
        Iterator<Map.Entry<String, RateEntry>> it = rateMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RateEntry> e = it.next();
            if (!e.getValue().minute.equals(currentMinute)) {
                it.remove();
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return request.getRemoteAddr();
    }

    private static class RateEntry {
        String minute;
        AtomicInteger count;

        RateEntry(String minute, AtomicInteger count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
