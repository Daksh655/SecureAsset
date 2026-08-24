package com.secureasset.backend.repository;

import com.secureasset.backend.entity.RecoveryAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecoveryActionRepository
        extends JpaRepository<RecoveryAction, UUID> {

    List<RecoveryAction> findByRecoveryCaseId(UUID recoveryCaseId);

    List<RecoveryAction> findByRecoveryCaseIdOrderByRequestedAtDesc(
            UUID recoveryCaseId
    );

    List<RecoveryAction> findByStatus(RecoveryAction.Status status);

    boolean existsByRecoveryCaseIdAndActionType(
            UUID recoveryCaseId,
            RecoveryAction.ActionType actionType
    );
}