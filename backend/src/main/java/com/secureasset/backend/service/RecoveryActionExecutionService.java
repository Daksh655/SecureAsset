package com.secureasset.backend.service;

import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.integration.RazorpayPaymentService;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RecoveryActionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryActionExecutionService.class);
    private static final BigDecimal AUTO_EXECUTE_LIMIT = new BigDecimal("10000.00");

    private final RecoveryCaseRepository caseRepository;
    private final RecoveryActionRepository actionRepository;
    private final AuditLogRepository auditLogRepository;
    private final RazorpayPaymentService razorpayPaymentService;

    public RecoveryActionExecutionService(
            RecoveryCaseRepository caseRepository,
            RecoveryActionRepository actionRepository,
            AuditLogRepository auditLogRepository,
            RazorpayPaymentService razorpayPaymentService) {
        this.caseRepository = caseRepository;
        this.actionRepository = actionRepository;
        this.auditLogRepository = auditLogRepository;
        this.razorpayPaymentService = razorpayPaymentService;
    }

    public RecoveryAction proposeAction(UUID caseId, RecoveryAction.ActionType actionType, BigDecimal amount) {
        RecoveryCase rc = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Recovery case not found"));

        if (rc.getStatus() == RecoveryCase.Status.RECOVERED) {
            throw new IllegalStateException("Cannot propose action for an already recovered case.");
        }

        if (rc.getEligibility() != RecoveryCase.Eligibility.ELIGIBLE) {
            throw new IllegalStateException("Case is not eligible for recovery.");
        }

        List<RecoveryAction> existingActions = actionRepository.findByRecoveryCaseId(caseId);
        if (existingActions.size() >= 2) {
            throw new IllegalStateException("Maximum recovery attempts reached.");
        }

        boolean hasActive = existingActions.stream()
                .anyMatch(a -> a.getStatus() == RecoveryAction.Status.PENDING ||
                               a.getStatus() == RecoveryAction.Status.APPROVED ||
                               a.getStatus() == RecoveryAction.Status.EXECUTING);
        if (hasActive) {
            throw new IllegalStateException("A recovery action is already active for this case.");
        }

        RecoveryAction action = new RecoveryAction();
        action.setRecoveryCase(rc);
        action.setActionType(actionType);
        action.setAmount(amount);
        action.setRequestedAt(OffsetDateTime.now());

        boolean requiresApproval = (actionType == RecoveryAction.ActionType.CREATE_PAYMENT_LINK) || (amount.compareTo(AUTO_EXECUTE_LIMIT) > 0);

        if (!requiresApproval) {
            action.setStatus(RecoveryAction.Status.APPROVED);
            action.setApprovalStatus(RecoveryAction.ApprovalStatus.NOT_REQUIRED);
            action.setApprovedAt(OffsetDateTime.now());
            logAudit(rc, action, AuditLog.EventType.ACTION_APPROVED, "Automatic approval granted", true);
        } else {
            action.setStatus(RecoveryAction.Status.PENDING);
            action.setApprovalStatus(RecoveryAction.ApprovalStatus.PENDING);
            rc.setStatus(RecoveryCase.Status.PENDING_APPROVAL);
            logAudit(rc, action, AuditLog.EventType.ACTION_APPROVAL_REQUESTED, "Manual approval required", true);
        }

        caseRepository.save(rc);
        return actionRepository.save(action);
    }

    public void approveAction(UUID actionId) {
        RecoveryAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found"));

        if (action.getStatus() != RecoveryAction.Status.PENDING) {
            throw new IllegalStateException("Action is not pending approval.");
        }

        action.setStatus(RecoveryAction.Status.APPROVED);
        action.setApprovalStatus(RecoveryAction.ApprovalStatus.APPROVED);
        action.setApprovedAt(OffsetDateTime.now());
        
        RecoveryCase rc = action.getRecoveryCase();
        logAudit(rc, action, AuditLog.EventType.ACTION_APPROVED, "Merchant approved the action", true);
        actionRepository.save(action);
    }

    public void rejectAction(UUID actionId, String reason) {
        RecoveryAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found"));

        if (action.getStatus() != RecoveryAction.Status.PENDING) {
            throw new IllegalStateException("Action is not pending approval.");
        }

        action.setStatus(RecoveryAction.Status.REJECTED);
        action.setApprovalStatus(RecoveryAction.ApprovalStatus.REJECTED);
        action.setCompletedAt(OffsetDateTime.now());

        RecoveryCase rc = action.getRecoveryCase();
        rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
        logAudit(rc, action, AuditLog.EventType.ACTION_REJECTED, "Merchant rejected the action: " + reason, true);

        actionRepository.save(action);
        caseRepository.save(rc);
    }

    public void approveActionByCase(UUID caseId, RecoveryAction.ActionType actionType, BigDecimal amount) {
        RecoveryAction pendingAction = actionRepository.findByRecoveryCaseId(caseId).stream()
                .filter(a -> a.getStatus() == RecoveryAction.Status.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No pending action found for this case"));

        if (pendingAction.getActionType() != actionType || pendingAction.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Action details do not match the pending recommendation.");
        }

        approveAction(pendingAction.getId());
    }

    public void rejectActionByCase(UUID caseId, String reason) {
        RecoveryAction pendingAction = actionRepository.findByRecoveryCaseId(caseId).stream()
                .filter(a -> a.getStatus() == RecoveryAction.Status.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No pending action found for this case"));

        rejectAction(pendingAction.getId(), reason);
    }

    public void executeAction(UUID actionId) {
        RecoveryAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found"));

        RecoveryCase rc = action.getRecoveryCase();

        // 1. Idempotency Check
        if (action.getStatus() == RecoveryAction.Status.SUCCESS || 
            action.getStatus() == RecoveryAction.Status.EXECUTING || 
            action.getStatus() == RecoveryAction.Status.FAILED) {
            log.info("Action {} is already processed or processing (Status: {}). Idempotently skipping.", actionId, action.getStatus());
            return;
        }

        // 2. Pre-execution validations
        if (action.getStatus() != RecoveryAction.Status.APPROVED) {
            throw new IllegalStateException("Action is not approved for execution.");
        }
        if (rc.getStatus() == RecoveryCase.Status.RECOVERED) {
            throw new IllegalStateException("Case is already recovered.");
        }
        if (rc.getEligibility() != RecoveryCase.Eligibility.ELIGIBLE) {
            throw new IllegalStateException("Case is not eligible for recovery.");
        }
        if (action.getAmount().compareTo(AUTO_EXECUTE_LIMIT) > 0 && action.getApprovalStatus() != RecoveryAction.ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Amount exceeds limit and lacks explicit manual approval.");
        }

        List<RecoveryAction> existingActions = actionRepository.findByRecoveryCaseId(rc.getId());
        
        long previousAttempts = existingActions.stream()
                .filter(a -> !a.getId().equals(actionId))
                .count();
        if (previousAttempts >= 2) {
            throw new IllegalStateException("Maximum recovery attempts reached.");
        }

        boolean hasDuplicateActive = existingActions.stream()
                .filter(a -> !a.getId().equals(actionId))
                .anyMatch(a -> a.getStatus() == RecoveryAction.Status.PENDING ||
                               a.getStatus() == RecoveryAction.Status.APPROVED ||
                               a.getStatus() == RecoveryAction.Status.EXECUTING);
        if (hasDuplicateActive) {
            throw new IllegalStateException("A recovery action is already active for this case.");
        }

        // 3. Mark EXECUTING to prevent race conditions
        action.setStatus(RecoveryAction.Status.EXECUTING);
        action.setExecutedAt(OffsetDateTime.now());
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        actionRepository.save(action);
        caseRepository.save(rc);

        logAudit(rc, action, AuditLog.EventType.RAZORPAY_REQUEST, "Initiating external execution: " + action.getActionType(), true);

        // 4. Execute external API call
        if (action.getActionType() == RecoveryAction.ActionType.CREATE_PAYMENT_LINK) {
            String custName = rc.getCustomer() != null ? rc.getCustomer().getName() : null;
            String custEmail = rc.getCustomer() != null ? rc.getCustomer().getEmail() : null;
            String custContact = rc.getCustomer() != null ? rc.getCustomer().getPhone() : null;

            RazorpayPaymentService.PaymentLinkResult result = razorpayPaymentService.createPaymentLink(
                    action.getAmount(),
                    "INR",
                    custName,
                    custEmail,
                    custContact,
                    action.getId().toString()
            );

            // 5. Handle result
            action.setCompletedAt(OffsetDateTime.now());
            if (result.success()) {
                action.setStatus(RecoveryAction.Status.SUCCESS);
                action.setRazorpayReference(result.paymentLinkId());
                action.setResult(result.shortUrl());
                rc.setStatus(RecoveryCase.Status.EXECUTING);
                
                logAudit(rc, action, AuditLog.EventType.RAZORPAY_RESPONSE, "Payment link created successfully: " + result.paymentLinkId(), true);
            } else {
                action.setStatus(RecoveryAction.Status.FAILED);
                action.setErrorMessage("Failed to create payment link.");
                rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
                
                logAudit(rc, action, AuditLog.EventType.RAZORPAY_RESPONSE, "Payment link creation failed.", false);
            }

            actionRepository.save(action);
            caseRepository.save(rc);
        } else {
            action.setStatus(RecoveryAction.Status.FAILED);
            action.setErrorMessage("Unsupported action type for automatic execution");
            action.setCompletedAt(OffsetDateTime.now());
            rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
            actionRepository.save(action);
            caseRepository.save(rc);
            logAudit(rc, action, AuditLog.EventType.ACTION_BLOCKED, "Unsupported action type executed", false);
        }
    }

    private void logAudit(RecoveryCase rc, RecoveryAction action, AuditLog.EventType type, String message, boolean success) {
        AuditLog al = new AuditLog();
        al.setRecoveryCase(rc);
        al.setRecoveryAction(action);
        al.setEventType(type);
        al.setActorType(AuditLog.ActorType.SYSTEM);
        al.setMessage(message);
        al.setSuccess(success);
        al.setCreatedAt(OffsetDateTime.now());
        auditLogRepository.save(al);
    }
}
