package com.admin.repository;

import com.admin.model.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    List<AdminSession> findByAdminIdAndActive(String adminId, boolean active);
}
