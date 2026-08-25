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

    long countByPriority(RecoveryCase.Priority priority);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(c.riskAmount) FROM RecoveryCase c")
    java.math.BigDecimal sumRiskAmount();

    @org.springframework.data.jpa.repository.Query("SELECT SUM(c.riskAmount) FROM RecoveryCase c WHERE c.status IN :statuses")
    java.math.BigDecimal sumRiskAmountByStatuses(@org.springframework.data.repository.query.Param("statuses") List<RecoveryCase.Status> statuses);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM RecoveryCase r WHERE " +
           "(:priority IS NULL OR r.priority = :priority) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:problemType IS NULL OR r.problemType = :problemType) AND " +
           "(:minAmount IS NULL OR r.riskAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR r.riskAmount <= :maxAmount) AND " +
           "(:minScore IS NULL OR r.recoveryScore >= :minScore)")
    org.springframework.data.domain.Page<RecoveryCase> searchCases(
            @org.springframework.data.repository.query.Param("priority") RecoveryCase.Priority priority,
            @org.springframework.data.repository.query.Param("status") RecoveryCase.Status status,
            @org.springframework.data.repository.query.Param("problemType") RecoveryCase.ProblemType problemType,
            @org.springframework.data.repository.query.Param("minAmount") java.math.BigDecimal minAmount,
            @org.springframework.data.repository.query.Param("maxAmount") java.math.BigDecimal maxAmount,
            @org.springframework.data.repository.query.Param("minScore") Integer minScore,
            org.springframework.data.domain.Pageable pageable);
}