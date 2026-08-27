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

    @org.springframework.data.jpa.repository.Query("SELECT a FROM AuditLog a WHERE " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:caseId IS NULL OR a.recoveryCase.id = :caseId)")
    org.springframework.data.domain.Page<AuditLog> searchAuditLogs(
            @org.springframework.data.repository.query.Param("eventType") AuditLog.EventType eventType,
            @org.springframework.data.repository.query.Param("caseId") UUID caseId,
            org.springframework.data.domain.Pageable pageable);
}