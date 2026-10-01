package com.guyi.access.filter;

import com.guyi.access.util.ClientIpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Access log for the API (design Task 19).
 *
 * <p>Deliberately records no request body, no Authorization header and no query string: card codes
 * and tokens must never end up in log files.
 *
 * <p>It is registered only in the servlet container (it is NOT added to the security filter chain),
 * so it executes exactly once per request - unlike the JWT/rate-limit filters, which needed explicit
 * de-registration - and it also observes responses produced by the security chain (401/403).
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Value("${app.trust-proxy:false}")
    private boolean trustProxy;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // Only API traffic, and skip the health probe (monitors poll it every few seconds)
        if (!path.startsWith("/api/") || "/api/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long tookMillis = System.currentTimeMillis() - start;
            int status = response.getStatus();
            String line = String.format("%s %s -> %d (%d ms) ip=%s",
                    request.getMethod(), path, status, tookMillis, ClientIpUtil.resolve(request, trustProxy));
            if (status >= 500) {
                log.error(line);
            } else if (status >= 400) {
                log.warn(line);
            } else {
                log.info(line);
            }
        }
    }
}
