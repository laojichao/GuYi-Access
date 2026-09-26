package com.guyi.access.service;

import com.guyi.access.entity.Admin;
import com.guyi.access.exception.BusinessException;
import com.guyi.access.repository.AdminRepository;
import com.guyi.access.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final AdminRepository adminRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AdminRepository adminRepository, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public String login(String username, String password) {
        Optional<Admin> adminOpt = adminRepository.findById(1);
        if (adminOpt.isEmpty()) {
            throw new BusinessException("系统未安装");
        }

        Admin admin = adminOpt.get();
        // Always run the bcrypt comparison so response timing does not reveal whether the
        // username or the password was wrong
        boolean usernameOk = admin.getUsername().equals(username);
        boolean passwordOk = passwordEncoder.matches(password, admin.getPasswordHash());
        if (!usernameOk || !passwordOk) {
            throw new BusinessException("用户名或密码错误");
        }

        return jwtUtil.generateToken(admin.getUsername());
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

    public void updatePassword(String newPassword) {
        Admin admin = adminRepository.findById(1)
                .orElseThrow(() -> new BusinessException("管理员不存在"));
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminRepository.save(admin);
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
        adminRepository.save(admin);
    }
}
