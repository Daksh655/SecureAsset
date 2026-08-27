package com.secureasset.backend.service;

import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class RevenueRiskEvaluationService {

    private final RevenueRiskService revenueRiskService;
    private final PaymentRepository paymentRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final TransactionTemplate transactionTemplate;

    public RevenueRiskEvaluationService(
            RevenueRiskService revenueRiskService,
            PaymentRepository paymentRepository,
            RecoveryCaseRepository recoveryCaseRepository,
            PlatformTransactionManager transactionManager) {
        this.revenueRiskService = revenueRiskService;
        this.paymentRepository = paymentRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void evaluateFailedPayments() {
        int page = 0;
        int size = 100;
        
        while (true) {
            Page<Payment> paymentPage = paymentRepository.findByStatus(
                    Payment.PaymentStatus.FAILED, PageRequest.of(page, size));
            
            if (paymentPage.isEmpty()) {
                break;
            }
            
            transactionTemplate.execute(status -> {
                for (Payment payment : paymentPage.getContent()) {
                    processFailedPayment(payment);
                }
                return null;
            });
            
            if (!paymentPage.hasNext()) {
                break;
            }
            page++;
        }
    }

    public int evaluateDemoFailedPayments(java.util.UUID datasetId) {
        int page = 0;
        int size = 100;
        int processedCount = 0;
        
        while (true) {
            Page<Payment> paymentPage = paymentRepository.findByStatusAndCustomerDatasetId(
                    Payment.PaymentStatus.FAILED, datasetId, PageRequest.of(page, size));
            
            if (paymentPage.isEmpty()) {
                break;
            }
            
            processedCount += paymentPage.getNumberOfElements();
            
            transactionTemplate.execute(status -> {
                for (Payment payment : paymentPage.getContent()) {
                    processFailedPayment(payment);
                }
                return null;
            });
            
            if (!paymentPage.hasNext()) {
                break;
            }
            page++;
        }
        return processedCount;
    }

    private void processFailedPayment(Payment payment) {
        // Prevent duplicate active cases for the same order
        boolean duplicateActiveCaseExists = recoveryCaseRepository.existsByOrderIdAndStatusIn(
                payment.getOrder().getId(),
                List.of(
                        RecoveryCase.Status.NEW,
                        RecoveryCase.Status.ANALYZING,
                        RecoveryCase.Status.ACTION_REQUIRED,
                        RecoveryCase.Status.PENDING_APPROVAL,
                        RecoveryCase.Status.EXECUTING
                )
        );
        
        // Count previous recovery cases for this order as attempts
        int previousAttempts = (int) recoveryCaseRepository.countByOrderId(payment.getOrder().getId());

        // Count previous successful payments for the customer
        int successfulCount = (int) paymentRepository.countByCustomerIdAndStatus(
                payment.getCustomer().getId(), Payment.PaymentStatus.CAPTURED);

        // Check if already recovered (order has a CAPTURED payment)
        boolean alreadyRecovered = paymentRepository.existsByOrderIdAndStatus(
                payment.getOrder().getId(), Payment.PaymentStatus.CAPTURED);

        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(payment.getAmount());
        context.setPreviousSuccessfulPayments(successfulCount);
        context.setFailureReason(payment.getFailureReason() != null ? payment.getFailureReason().name() : "UNKNOWN");
        context.setEventTime(payment.getCreatedAt()); 
        context.setPreviousRecoveryAttempts(previousAttempts);
        context.setAlreadyRecovered(alreadyRecovered);
        context.setDuplicateActiveCaseExists(duplicateActiveCaseExists);
        context.setMissingContext(payment.getCustomer() == null || payment.getOrder() == null);
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        
        RevenueRiskService.AssessmentResult result = revenueRiskService.evaluateCandidate(context, now);
        
        if (result.isEligible()) {
            RecoveryCase rc = new RecoveryCase();
            rc.setCustomer(payment.getCustomer());
            rc.setOrder(payment.getOrder());
            rc.setPayment(payment);
            
            long failedCount = paymentRepository.countByOrderIdAndStatus(
                    payment.getOrder().getId(), Payment.PaymentStatus.FAILED);
            
            if (failedCount >= 2) {
                rc.setProblemType(RecoveryCase.ProblemType.REPEATED_PAYMENT_FAILURE);
            } else {
                rc.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
            }
            
            rc.setRiskAmount(payment.getAmount());
            rc.setRecoveryScore(result.getScore());
            rc.setPriority(RecoveryCase.Priority.valueOf(result.getPriority()));
            rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
            rc.setStatus(RecoveryCase.Status.NEW);
            rc.setAgentStatus(RecoveryCase.AgentStatus.NOT_ANALYZED);
            rc.setDetectedAt(now);
            rc.setUpdatedAt(now);
            
            recoveryCaseRepository.save(rc);
        }
    }
}
