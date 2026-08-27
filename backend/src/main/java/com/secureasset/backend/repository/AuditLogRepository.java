package com.secureasset.backend.repository;

import com.secureasset.backend.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE " +
           "a IN (SELECT al FROM AuditLog al JOIN al.recoveryCase rc JOIN rc.customer c WHERE c.dataset.id = :datasetId) " +
           "OR a IN (SELECT al2 FROM AuditLog al2 JOIN al2.recoveryAction ra JOIN ra.recoveryCase rc2 JOIN rc2.customer c2 WHERE c2.dataset.id = :datasetId)")
    void deleteByDatasetId(@Param("datasetId") UUID datasetId);

    List<AuditLog> findByRecoveryCaseIdOrderByCreatedAtAsc(
            UUID recoveryCaseId
    );

    List<AuditLog> findByRecoveryActionIdOrderByCreatedAtAsc(
            UUID recoveryActionId
    );

    List<AuditLog> findByRecoveryCaseIdOrderByCreatedAtDesc(UUID recoveryCaseId);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(a IN (SELECT al FROM AuditLog al JOIN al.recoveryCase rc JOIN rc.customer c WHERE c.dataset.id = :datasetId) " +
           " OR a IN (SELECT al2 FROM AuditLog al2 JOIN al2.recoveryAction ra JOIN ra.recoveryCase rc2 JOIN rc2.customer c2 WHERE c2.dataset.id = :datasetId)) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:caseId IS NULL OR a.recoveryCase.id = :caseId)")
    org.springframework.data.domain.Page<AuditLog> searchAuditLogsScoped(
            @Param("datasetId") UUID datasetId,
            @Param("eventType") AuditLog.EventType eventType,
            @Param("caseId") UUID caseId,
            org.springframework.data.domain.Pageable pageable);
}
