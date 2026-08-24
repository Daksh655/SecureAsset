package com.secureasset.backend.repository;

import com.secureasset.backend.entity.RecoveryCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID> {

    List<RecoveryCase> findByPriority(RecoveryCase.Priority priority);

    List<RecoveryCase> findByStatus(RecoveryCase.Status status);

    List<RecoveryCase> findByProblemType(
            RecoveryCase.ProblemType problemType
    );

    List<RecoveryCase> findByCustomerId(UUID customerId);

    List<RecoveryCase> findByPaymentId(UUID paymentId);

    List<RecoveryCase> findByPriorityAndStatusOrderByRecoveryScoreDesc(
            RecoveryCase.Priority priority,
            RecoveryCase.Status status
    );

    long countByOrderId(UUID orderId);

    boolean existsByOrderIdAndStatusIn(UUID orderId, List<RecoveryCase.Status> statuses);
}