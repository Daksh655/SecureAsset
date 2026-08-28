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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RecoveryActionExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryActionExecutionService.class);
    
    private static final BigDecimal AUTO_EXECUTE_LIMIT = new BigDecimal("5000.00");

    private final RecoveryActionRepository actionRepository;
    private final RecoveryCaseRepository caseRepository;
    private final AuditLogRepository auditLogRepository;
    private final RazorpayPaymentService razorpayPaymentService;
    
    private RecoveryActionExecutionService self;

    public RecoveryActionExecutionService(RecoveryActionRepository actionRepository, 
                                          RecoveryCaseRepository caseRepository,
                                          AuditLogRepository auditLogRepository,
                                          RazorpayPaymentService razorpayPaymentService) {
        this.actionRepository = actionRepository;
        this.caseRepository = caseRepository;
        this.auditLogRepository = auditLogRepository;
        this.razorpayPaymentService = razorpayPaymentService;
    }

    @Autowired
    public void setSelf(@Lazy RecoveryActionExecutionService self) {
        this.self = self;
    }

    @Transactional
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
        } else {
            action.setStatus(RecoveryAction.Status.PENDING);
            action.setApprovalStatus(RecoveryAction.ApprovalStatus.PENDING);
            rc.setStatus(RecoveryCase.Status.PENDING_APPROVAL);
        }

        rc = caseRepository.save(rc);
        action = actionRepository.save(action);

        if (!requiresApproval) {
            logAudit(rc, action, AuditLog.EventType.ACTION_APPROVED, "Automatic approval granted", true);
        } else {
            logAudit(rc, action, AuditLog.EventType.ACTION_APPROVAL_REQUESTED, "Manual approval required", true);
        }

        return action;
    }

    @Transactional
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
        
        action = actionRepository.save(action);
        
        logAudit(rc, action, AuditLog.EventType.ACTION_APPROVED, "Merchant approved the action", true);
    }

    @Transactional
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
        
        rc = caseRepository.save(rc);
        action = actionRepository.save(action);
        
        logAudit(rc, action, AuditLog.EventType.ACTION_REJECTED, "Merchant rejected the action: " + reason, true);
    }

    @Transactional
    public UUID approveActionByCase(UUID caseId, RecoveryAction.ActionType actionType, BigDecimal amount) {
        RecoveryAction pendingAction = actionRepository.findByRecoveryCaseId(caseId).stream()
                .filter(a -> a.getStatus() == RecoveryAction.Status.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No pending action found for this case"));

        if (pendingAction.getActionType() != actionType || pendingAction.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Action details do not match the pending recommendation.");
        }

        self.approveAction(pendingAction.getId());
        return pendingAction.getId();
    }

    @Transactional
    public void rejectActionByCase(UUID caseId, String reason) {
        RecoveryAction pendingAction = actionRepository.findByRecoveryCaseId(caseId).stream()
                .filter(a -> a.getStatus() == RecoveryAction.Status.PENDING)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No pending action found for this case"));

        self.rejectAction(pendingAction.getId(), reason);
    }

    public record ExecutionContext(UUID actionId, BigDecimal amount, String custName, String custEmail, String custContact, RecoveryAction.ActionType actionType) {}

    public void executeAction(UUID actionId) {
        ExecutionContext ctx = self.prepareExecution(actionId);
        if (ctx == null) {
            return; // Idempotently skipped or failed preparation
        }

        if (ctx.actionType() == RecoveryAction.ActionType.CREATE_PAYMENT_LINK) {
            RazorpayPaymentService.PaymentLinkResult result = null;
            try {
                result = razorpayPaymentService.createPaymentLink(
                        ctx.amount(),
                        "INR",
                        ctx.custName(),
                        ctx.custEmail(),
                        ctx.custContact(),
                        ctx.actionId().toString()
                );
            } catch (Exception e) {
                log.error("Failed external API call", e);
                self.handleExecutionFailure(ctx.actionId(), "Failed to create payment link: " + e.getMessage(), AuditLog.EventType.ACTION_BLOCKED);
                return;
            }

            if (result.success()) {
                self.handleExecutionSuccess(ctx.actionId(), result.paymentLinkId(), result.shortUrl());
            } else {
                self.handleExecutionFailure(ctx.actionId(), "Payment link creation failed.", AuditLog.EventType.RAZORPAY_RESPONSE);
            }
        } else {
            self.handleExecutionFailure(ctx.actionId(), "Unsupported action type for automatic execution", AuditLog.EventType.ACTION_BLOCKED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ExecutionContext prepareExecution(UUID actionId) {
        RecoveryAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found"));

        // 1. Idempotency Check
        if (action.getStatus() == RecoveryAction.Status.SUCCESS || 
            action.getStatus() == RecoveryAction.Status.EXECUTING || 
            action.getStatus() == RecoveryAction.Status.FAILED) {
            log.info("Action {} is already processed or processing (Status: {}). Idempotently skipping.", actionId, action.getStatus());
            return null;
        }

        RecoveryCase rc = action.getRecoveryCase();

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

        // 3. Mark EXECUTING
        action.setStatus(RecoveryAction.Status.EXECUTING);
        action.setExecutedAt(OffsetDateTime.now());
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        
        rc = caseRepository.save(rc);
        action = actionRepository.save(action);

        logAudit(rc, action, AuditLog.EventType.RAZORPAY_REQUEST, "Initiating external execution: " + action.getActionType(), true);

        // Map lazy properties to detached records while session is active
        String custName = rc.getCustomer() != null ? rc.getCustomer().getName() : null;
        String custEmail = rc.getCustomer() != null ? rc.getCustomer().getEmail() : null;
        String custContact = rc.getCustomer() != null ? rc.getCustomer().getPhone() : null;

        return new ExecutionContext(action.getId(), action.getAmount(), custName, custEmail, custContact, action.getActionType());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleExecutionSuccess(UUID actionId, String razorpayReference, String resultUrl) {
        RecoveryAction action = actionRepository.findById(actionId).orElseThrow();
        RecoveryCase rc = action.getRecoveryCase();
        
        action.setCompletedAt(OffsetDateTime.now());
        action.setStatus(RecoveryAction.Status.SUCCESS);
        action.setRazorpayReference(razorpayReference);
        action.setResult(resultUrl);
        
        // Ensure case is EXECUTING, do not mark as RECOVERED yet
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        
        rc = caseRepository.save(rc);
        action = actionRepository.save(action);
        
        logAudit(rc, action, AuditLog.EventType.RAZORPAY_RESPONSE, "Payment link created successfully: " + razorpayReference, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleExecutionFailure(UUID actionId, String errorMessage, AuditLog.EventType auditType) {
        RecoveryAction action = actionRepository.findById(actionId).orElseThrow();
        RecoveryCase rc = action.getRecoveryCase();
        
        action.setStatus(RecoveryAction.Status.FAILED);
        action.setErrorMessage(errorMessage);
        action.setCompletedAt(OffsetDateTime.now());
        
        rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
        
        rc = caseRepository.save(rc);
        action = actionRepository.save(action);
        
        logAudit(rc, action, auditType, errorMessage, false);
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
