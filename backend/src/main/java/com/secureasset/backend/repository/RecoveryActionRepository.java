package com.secureasset.backend.repository;

import com.secureasset.backend.entity.RecoveryAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecoveryActionRepository
        extends JpaRepository<RecoveryAction, UUID> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM RecoveryAction r WHERE r.recoveryCase.customer.dataset.id = :datasetId")
    void deleteByRecoveryCaseCustomerDatasetId(@org.springframework.data.repository.query.Param("datasetId") UUID datasetId);

    List<RecoveryAction> findByRecoveryCaseId(UUID recoveryCaseId);

    List<RecoveryAction> findByRecoveryCaseIdOrderByRequestedAtDesc(
            UUID recoveryCaseId
    );

    List<RecoveryAction> findByRecoveryCaseIdAndStatus(UUID recoveryCaseId, RecoveryAction.Status status);

    List<RecoveryAction> findByStatus(RecoveryAction.Status status);

    boolean existsByRecoveryCaseIdAndActionType(
            UUID recoveryCaseId, RecoveryAction.ActionType actionType
    );

    RecoveryAction findByRazorpayReference(String razorpayReference);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM RecoveryAction r WHERE " +
           "(r.recoveryCase.customer.dataset.id = :datasetId) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:approvalStatus IS NULL OR r.approvalStatus = :approvalStatus) AND " +
           "(:actionType IS NULL OR r.actionType = :actionType)")
    org.springframework.data.domain.Page<RecoveryAction> searchActionsScoped(
            @org.springframework.data.repository.query.Param("datasetId") UUID datasetId,
            @org.springframework.data.repository.query.Param("status") RecoveryAction.Status status,
            @org.springframework.data.repository.query.Param("approvalStatus") RecoveryAction.ApprovalStatus approvalStatus,
            @org.springframework.data.repository.query.Param("actionType") RecoveryAction.ActionType actionType,
            org.springframework.data.domain.Pageable pageable);
}
