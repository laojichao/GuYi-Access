package com.guyi.access.service;

import com.guyi.access.entity.Admin;
import com.guyi.access.exception.AccountLockedException;
import com.guyi.access.exception.BusinessException;
import com.guyi.access.repository.AdminRepository;
import com.guyi.access.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AdminRepository adminRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.login.lock-minutes:15}")
    private long lockMinutes;

    /**
     * Failed-login counters keyed by "username|clientIp". The IP is part of the key on purpose: with
     * a single administrator account, a username-only lockout would let anyone lock the operator out
     * of the system by submitting wrong passwords.
     */
    private final Map<String, LoginAttempt> attempts = new ConcurrentHashMap<>();

    public AuthService(AdminRepository adminRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public String login(String username, String password, String clientIp) {
        Optional<Admin> adminOpt = adminRepository.findById(1);
        if (adminOpt.isEmpty()) {
            throw new BusinessException("系统未安装");
        }

        Admin admin = adminOpt.get();
        String attemptKey = username + "|" + (clientIp == null ? "" : clientIp);
        LoginAttempt attempt = attempts.computeIfAbsent(attemptKey, key -> new LoginAttempt());

        long lockedMillis = attempt.lockedForMillis();
        if (lockedMillis > 0) {
            long remainingMinutes = Math.max(1, lockedMillis / 60_000 + 1);
            throw new AccountLockedException("登录失败次数过多，请 " + remainingMinutes + " 分钟后再试");
        }

        // Always run the bcrypt comparison so response timing does not reveal whether the
        // username or the password was wrong
        boolean usernameOk = admin.getUsername().equals(username);
        boolean passwordOk = passwordEncoder.matches(password, admin.getPasswordHash());
        if (!usernameOk || !passwordOk) {
            boolean lockedNow = attempt.recordFailure(maxFailedAttempts, lockMinutes);
            if (lockedNow) {
                log.warn("登录失败次数达到上限，已按「用户名+IP」锁定 {} 分钟（username={}）", lockMinutes, username);
            }
            throw new BusinessException("用户名或密码错误");
        }

        attempts.remove(attemptKey);
        return jwtUtil.generateToken(admin.getUsername(), currentTokenVersion(admin));
    }

    /** Used by the install endpoint, which has no client IP context of its own. */
    public String login(String username, String password) {
        return login(username, password, null);
    }

    /** Bumps the revocation version so every previously issued token stops being accepted. */
    @Transactional
    public void revokeAllTokens() {
        adminRepository.findById(1).ifPresent(admin -> {
            admin.setTokenVersion(currentTokenVersion(admin) + 1);
            adminRepository.save(admin);
            log.info("已吊销全部已签发令牌（token_version -> {}）", admin.getTokenVersion());
        });
    }

    public boolean verifyPassword(String password) {
        return adminRepository.findById(1)
                .map(admin -> passwordEncoder.matches(password, admin.getPasswordHash()))
                .orElse(false);
    }

    public String getAdminUsername() {
        return adminRepository.findById(1)
                .map(Admin::getUsername)
                .orElse("GuYi");
    }

    /**
     * @return a freshly minted token, because the password change revokes every older token
     *         (including the caller's own) - returning a new one keeps the operator logged in.
     */
    @Transactional
    public String updatePassword(String newPassword) {
        Admin admin = adminRepository.findById(1)
                .orElseThrow(() -> new BusinessException("管理员不存在"));
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admin.setTokenVersion(currentTokenVersion(admin) + 1);
        adminRepository.save(admin);
        return jwtUtil.generateToken(admin.getUsername(), currentTokenVersion(admin));
    }

    public void updateUsername(String newUsername) {
        Admin admin = adminRepository.findById(1)
                .orElseThrow(() -> new BusinessException("管理员不存在"));
        admin.setUsername(newUsername);
        adminRepository.save(admin);
    }

    public String getAdminPasswordHash() {
        return adminRepository.findById(1)
                .map(Admin::getPasswordHash)
                .orElse("");
    }

    public boolean isInstalled() {
        return adminRepository.findById(1).isPresent();
    }

    @Transactional
    public synchronized void initAdmin(String username, String password) {
        if (adminRepository.findById(1).isPresent()) {
            throw new BusinessException("系统已安装");
        }
        Admin admin = new Admin();
        admin.setId(1);
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setTokenVersion(0);
        adminRepository.save(admin);
    }

    private int currentTokenVersion(Admin admin) {
        return admin.getTokenVersion() == null ? 0 : admin.getTokenVersion();
    }

    /** Per (username, IP) failure counter with a temporary lock. */
    private static class LoginAttempt {
        private int failures;
        private long lockedUntilMillis;

        synchronized long lockedForMillis() {
            return Math.max(lockedUntilMillis - System.currentTimeMillis(), 0);
        }

        synchronized boolean recordFailure(int maxAttempts, long lockMinutes) {
            failures++;
            if (failures >= maxAttempts) {
                lockedUntilMillis = System.currentTimeMillis() + lockMinutes * 60_000L;
                failures = 0;
                return true;
            }
            return false;
        }
    }
}
