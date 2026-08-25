package com.secureasset.backend.service;

import com.secureasset.backend.dto.*;
import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecoveryCaseService {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PaymentRepository paymentRepository;

    public RecoveryCaseService(RecoveryCaseRepository recoveryCaseRepository,
                               RecoveryActionRepository recoveryActionRepository,
                               AuditLogRepository auditLogRepository,
                               PaymentRepository paymentRepository) {
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.auditLogRepository = auditLogRepository;
        this.paymentRepository = paymentRepository;
    }

    public PageResponse<RecoveryCaseSummaryDto> searchCases(
            String priority, String status, String problemType,
            BigDecimal minAmount, BigDecimal maxAmount, Integer minScore,
            int page, int size) {
        
        if (size > 100) size = 100;

        RecoveryCase.Priority pEnum = priority != null ? RecoveryCase.Priority.valueOf(priority.toUpperCase()) : null;
        RecoveryCase.Status sEnum = status != null ? RecoveryCase.Status.valueOf(status.toUpperCase()) : null;
        RecoveryCase.ProblemType ptEnum = problemType != null ? RecoveryCase.ProblemType.valueOf(problemType.toUpperCase()) : null;

        // Order by priority (HIGH, MEDIUM, LOW) then by score desc
        Sort sort = Sort.by(Sort.Order.asc("priority"), Sort.Order.desc("recoveryScore"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<RecoveryCase> casePage = recoveryCaseRepository.searchCases(
                pEnum, sEnum, ptEnum, minAmount, maxAmount, minScore, pageRequest);

        List<RecoveryCaseSummaryDto> content = casePage.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());

        return new PageResponse<>(content, casePage.getNumber(), casePage.getSize(),
                casePage.getTotalElements(), casePage.getTotalPages());
    }

    public RecoveryCaseDetailDto getCaseDetails(UUID id) {
        RecoveryCase rc = recoveryCaseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found"));

        return new RecoveryCaseDetailDto(
                rc.getId(),
                new RecoveryCaseDetailDto.CustomerSummaryDto(
                        rc.getCustomer().getId(),
                        rc.getCustomer().getName(),
                        rc.getCustomer().getEmail()
                ),
                rc.getOrder() != null ? new RecoveryCaseDetailDto.OrderSummaryDto(
                        rc.getOrder().getId(),
                        rc.getOrder().getAmount(),
                        rc.getOrder().getCurrency(),
                        rc.getOrder().getStatus().name()
                ) : null,
                rc.getPayment() != null ? new RecoveryCaseDetailDto.PaymentSummaryDto(
                        rc.getPayment().getId(),
                        rc.getPayment().getAmount(),
                        rc.getPayment().getStatus().name(),
                        rc.getPayment().getFailureReason() != null ? rc.getPayment().getFailureReason().name() : null,
                        rc.getPayment().getAttemptNumber()
                ) : null,
                rc.getProblemType().name(),
                rc.getRiskAmount(),
                rc.getRecoveryScore(),
                rc.getPriority().name(),
                rc.getStatus().name(),
                rc.getAgentStatus().name(),
                rc.getAgentRecommendation() != null ? rc.getAgentRecommendation().name() : null,
                rc.getAgentConfidence(),
                rc.getAgentReason(),
                rc.getDetectedAt(),
                rc.getAnalyzedAt()
        );
    }

    public List<RecoveryActionDto> getCaseActions(UUID id) {
        if (!recoveryCaseRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found");
        }
        return recoveryActionRepository.findByRecoveryCaseIdOrderByRequestedAtDesc(id).stream()
                .map(action -> new RecoveryActionDto(
                        action.getId(),
                        action.getActionType().name(),
                        action.getAmount(),
                        action.getStatus().name(),
                        action.getApprovalStatus().name(),
                        action.getRazorpayReference(),
                        action.getResult(),
                        action.getRequestedAt(),
                        action.getExecutedAt()
                )).collect(Collectors.toList());
    }

    public List<AuditLogDto> getCaseAuditLogs(UUID id) {
        if (!recoveryCaseRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found");
        }
        return auditLogRepository.findByRecoveryCaseIdOrderByCreatedAtAsc(id).stream()
                .map(log -> new AuditLogDto(
                        log.getId(),
                        log.getEventType().name(),
                        log.getActorType().name(),
                        log.getToolName(),
                        log.getMessage(),
                        log.isSuccess(),
                        log.getCreatedAt()
                )).collect(Collectors.toList());
    }

    public DashboardMetricsDto getDashboardMetrics() {
        long analyzed = paymentRepository.count();
        long opportunities = recoveryCaseRepository.count();
        long high = recoveryCaseRepository.countByPriority(RecoveryCase.Priority.HIGH);
        long medium = recoveryCaseRepository.countByPriority(RecoveryCase.Priority.MEDIUM);
        long low = recoveryCaseRepository.countByPriority(RecoveryCase.Priority.LOW);

        BigDecimal revenueAtRisk = recoveryCaseRepository.sumRiskAmount();
        if (revenueAtRisk == null) revenueAtRisk = BigDecimal.ZERO;

        BigDecimal potentiallyRecoverable = recoveryCaseRepository.sumRiskAmountByStatuses(
                List.of(RecoveryCase.Status.NEW, RecoveryCase.Status.ANALYZING, 
                        RecoveryCase.Status.ACTION_REQUIRED, RecoveryCase.Status.PENDING_APPROVAL, 
                        RecoveryCase.Status.EXECUTING)
        );
        if (potentiallyRecoverable == null) potentiallyRecoverable = BigDecimal.ZERO;

        BigDecimal recovered = recoveryCaseRepository.sumRiskAmountByStatuses(
                List.of(RecoveryCase.Status.RECOVERED)
        );
        if (recovered == null) recovered = BigDecimal.ZERO;

        BigDecimal rate = BigDecimal.ZERO;
        if (potentiallyRecoverable.compareTo(BigDecimal.ZERO) > 0) {
            rate = recovered.multiply(new BigDecimal("100"))
                    .divide(potentiallyRecoverable, 2, RoundingMode.HALF_UP);
        }

        return new DashboardMetricsDto(
                analyzed, opportunities, high, medium, low,
                revenueAtRisk, potentiallyRecoverable, recovered, rate, "INR"
        );
    }

    private RecoveryCaseSummaryDto mapToSummaryDto(RecoveryCase rc) {
        return new RecoveryCaseSummaryDto(
                rc.getId(),
                rc.getCustomer().getId(),
                rc.getOrder() != null ? rc.getOrder().getId() : null,
                rc.getPayment() != null ? rc.getPayment().getId() : null,
                rc.getProblemType().name(),
                rc.getRiskAmount(),
                rc.getRecoveryScore(),
                rc.getPriority().name(),
                rc.getStatus().name(),
                rc.getAgentStatus().name(),
                rc.getDetectedAt()
        );
    }
}
