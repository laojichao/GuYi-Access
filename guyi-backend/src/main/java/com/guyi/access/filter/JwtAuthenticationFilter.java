package com.guyi.access.filter;

import com.guyi.access.entity.Admin;
import com.guyi.access.repository.AdminRepository;
import com.guyi.access.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AdminRepository adminRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AdminRepository adminRepository) {
        this.jwtUtil = jwtUtil;
        this.adminRepository = adminRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token) && isTokenVersionCurrent(token)) {
                String username = jwtUtil.getUsernameFromToken(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Rejects tokens issued before the last revocation (logout / password change) by comparing the
     * token's "ver" claim with the stored counter.
     *
     * <p>Costs one row read per authenticated request - acceptable for a single-admin system, and the
     * client verify endpoint (which sends no Authorization header) never pays it.
     */
    private boolean isTokenVersionCurrent(String token) {
        Optional<Admin> admin = adminRepository.findById(1);
        if (admin.isEmpty()) {
            return false;
        }
        Integer stored = admin.get().getTokenVersion();
        Integer presented = jwtUtil.getTokenVersion(token);
        return (stored == null ? 0 : stored) == (presented == null ? 0 : presented);
    }
}
