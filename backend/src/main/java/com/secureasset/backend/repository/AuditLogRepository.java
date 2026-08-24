package com.secureasset.backend.repository;

import com.secureasset.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByRecoveryCaseIdOrderByCreatedAtAsc(
            UUID recoveryCaseId
    );

    List<AuditLog> findByRecoveryActionIdOrderByCreatedAtAsc(
            UUID recoveryActionId
    );
}