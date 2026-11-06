package com.admin.service;

import com.admin.controller.dto.AdminRequest;
import com.admin.model.Admin;
import com.admin.repository.AdminRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class AdminService {

    private final AdminRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Admin> findAll() { return repository.findAll(); }

    public Optional<Admin> findById(String adminId) { return repository.findById(adminId); }

    public Optional<Admin> findByUsername(String username) { return repository.findByUsername(username); }

    @Transactional
    public Admin create(AdminRequest req) {
        Admin a = new Admin();
        a.setUsername(req.getUsername());
        if (req.getPassword() == null || req.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        a.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        a.setAssignedAppId(req.getAssignedAppId());
    a.setTwoFactorEnabled(true);
    a.setTelegramUserId(req.getTelegramUserId());
        a.setExpiryPlan(req.getExpiryPlan() == null ? Admin.ExpiryPlan.ONE_DAY : req.getExpiryPlan());
        a.setStatus(Admin.AdminStatus.INACTIVE); // always start inactive
        return repository.save(a);
    }

    @Transactional
    public Optional<Admin> update(String adminId, AdminRequest req) {
        return repository.findById(adminId).map(existing -> {
            existing.setUsername(req.getUsername() != null ? req.getUsername() : existing.getUsername());
            if (req.getPassword() != null && !req.getPassword().isBlank()) {
                if (req.getPassword().length() < 8) throw new IllegalArgumentException("Password must be at least 8 characters");
                existing.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            }
            existing.setAssignedAppId(req.getAssignedAppId());
            existing.setTwoFactorEnabled(true);
            existing.setTelegramUserId(req.getTelegramUserId());
            if (req.getExpiryPlan() != null) existing.setExpiryPlan(req.getExpiryPlan());
            // Status rules: cannot revert to INACTIVE once active; allow BLOCKED; allow un-blocking to ACTIVE only if previously active and not expired
            if (req.getStatus() != null) {
                Admin.AdminStatus newStatus = req.getStatus();
                Admin.AdminStatus cur = existing.getStatus();
                if (cur == Admin.AdminStatus.INACTIVE) {
                    // Only allow moving to BLOCKED from INACTIVE via panel, otherwise activation will set ACTIVE on first login
                    if (newStatus == Admin.AdminStatus.BLOCKED) existing.setStatus(Admin.AdminStatus.BLOCKED);
                } else if (cur == Admin.AdminStatus.ACTIVE) {
                    if (newStatus == Admin.AdminStatus.BLOCKED) existing.setStatus(Admin.AdminStatus.BLOCKED);
                } else if (cur == Admin.AdminStatus.BLOCKED) {
                    if (newStatus == Admin.AdminStatus.ACTIVE) existing.setStatus(Admin.AdminStatus.ACTIVE);
                }
            }
            return repository.save(existing);
        });
    }

    @Transactional
    public void deleteById(String adminId) { repository.deleteById(adminId); }

    @Transactional
    public Admin activateOnFirstLogin(Admin admin) {
        if (admin.getStatus() == Admin.AdminStatus.INACTIVE) {
            admin.setStatus(Admin.AdminStatus.ACTIVE);
            admin.setActivatedAt(Instant.now());
            admin.setExpiresAt(calculateExpiry(admin.getExpiryPlan(), admin.getActivatedAt()));
            repository.save(admin);
        }
        return admin;
    }

    public static Instant calculateExpiry(Admin.ExpiryPlan plan, Instant from) {
        if (plan == null) plan = Admin.ExpiryPlan.ONE_DAY;
        return switch (plan) {
            case ONE_DAY -> from.plus(1, ChronoUnit.DAYS);
            case THREE_DAYS -> from.plus(3, ChronoUnit.DAYS);
            case ONE_MONTH -> from.plus(30, ChronoUnit.DAYS);
            case ONE_YEAR -> from.plus(365, ChronoUnit.DAYS);
        };
    }
}
