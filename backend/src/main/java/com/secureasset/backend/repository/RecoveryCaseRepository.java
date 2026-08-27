package com.secureasset.backend.repository;

import com.secureasset.backend.entity.RecoveryCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID> {

    @Modifying
    @Query("DELETE FROM RecoveryCase r WHERE r.customer.dataset.id = :datasetId")
    void deleteByCustomerDatasetId(@Param("datasetId") UUID datasetId);

    long countByCustomerDatasetId(UUID datasetId);

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

    long countByPriorityAndCustomerDatasetId(RecoveryCase.Priority priority, UUID datasetId);

    @Query("SELECT SUM(c.riskAmount) FROM RecoveryCase c WHERE c.customer.dataset.id = :datasetId")
    BigDecimal sumRiskAmountByCustomerDatasetId(@Param("datasetId") UUID datasetId);

    @Query("SELECT SUM(c.riskAmount) FROM RecoveryCase c WHERE c.status IN :statuses AND c.customer.dataset.id = :datasetId")
    BigDecimal sumRiskAmountByStatusesAndCustomerDatasetId(@Param("statuses") List<RecoveryCase.Status> statuses, @Param("datasetId") UUID datasetId);

    @Query("SELECT r FROM RecoveryCase r WHERE " +
           "(r.customer.dataset.id = :datasetId) AND " +
           "(:priority IS NULL OR r.priority = :priority) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:problemType IS NULL OR r.problemType = :problemType) AND " +
           "(:minAmount IS NULL OR r.riskAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR r.riskAmount <= :maxAmount) AND " +
           "(:minScore IS NULL OR r.recoveryScore >= :minScore)")
    org.springframework.data.domain.Page<RecoveryCase> searchCasesScoped(
            @Param("datasetId") UUID datasetId,
            @Param("priority") RecoveryCase.Priority priority,
            @Param("status") RecoveryCase.Status status,
            @Param("problemType") RecoveryCase.ProblemType problemType,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("minScore") Integer minScore,
            org.springframework.data.domain.Pageable pageable);
}
