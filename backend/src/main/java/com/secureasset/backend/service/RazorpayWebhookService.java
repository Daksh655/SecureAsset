package com.secureasset.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureasset.backend.dto.razorpay.RazorpayPaymentLinkWebhookPayload;
import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.ProcessedWebhookEvent;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.ProcessedWebhookEventRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final AuditLogRepository auditLogRepository;

    public RazorpayWebhookService(
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            RecoveryActionRepository recoveryActionRepository,
            RecoveryCaseRepository recoveryCaseRepository,
            AuditLogRepository auditLogRepository) {
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void processWebhook(String rawPayload, String eventId) {
        log.info("[DIAGNOSTIC] Webhook received. Event ID: {}", eventId);
        
        if (eventId != null && !eventId.isEmpty()) {
            if (processedWebhookEventRepository.existsById(eventId)) {
                log.info("[DIAGNOSTIC] Webhook event {} already processed. Ignoring.", eventId);
                logAudit(null, null, AuditLog.EventType.WEBHOOK_DUPLICATE_IGNORED, "Ignored duplicate webhook event: " + eventId, true);
                return;
            }
        }

        RazorpayPaymentLinkWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, RazorpayPaymentLinkWebhookPayload.class);
        } catch (Exception e) {
            log.error("[DIAGNOSTIC] Malformed webhook payload", e);
            logAuditWithOutput(null, null, AuditLog.EventType.WEBHOOK_RECONCILIATION_FAILED, "Malformed payload", "{\"reason\":\"MALFORMED_JSON\"}", false);
            return;
        }

        String event = payload.event();
        log.info("[DIAGNOSTIC] Parsed Event Type: {}", event);

        if (event == null) {
            log.warn("[DIAGNOSTIC] Webhook payload missing event type");
            return; 
        }

        logAudit(null, null, AuditLog.EventType.WEBHOOK_RECEIVED, "Received event: " + event, true);

        boolean success = true;

        if (event.startsWith("payment_link.")) {
            success = processPaymentLinkEvent(payload, eventId);
        } else {
            log.info("[DIAGNOSTIC] Ignored unknown event type: {}", event);
            logAuditWithOutput(null, null, AuditLog.EventType.WEBHOOK_RECONCILIATION_FAILED, "Unknown event type: " + event, "{\"reason\":\"UNKNOWN_EVENT\"}", false);
        }

        if (success && eventId != null && !eventId.isEmpty()) {
            log.info("[DIAGNOSTIC] Saving processed event: {}", eventId);
            processedWebhookEventRepository.save(new ProcessedWebhookEvent(eventId, OffsetDateTime.now(ZoneOffset.UTC)));
        }
    }

    private boolean processPaymentLinkEvent(RazorpayPaymentLinkWebhookPayload payload, String eventId) {
        if (payload.payload() == null || payload.payload().paymentLink() == null || payload.payload().paymentLink().entity() == null) {
            log.warn("[DIAGNOSTIC] Missing payment_link entity in payload");
            logAuditWithOutput(null, null, AuditLog.EventType.WEBHOOK_RECONCILIATION_FAILED, "Missing payment_link entity", "{\"reason\":\"MALFORMED_BUSINESS_DATA\"}", false);
            return false;
        }

        var plink = payload.payload().paymentLink().entity();
        String plinkId = plink.id();
        log.info("[DIAGNOSTIC] Extracted payment-link ID: {}", plinkId);

        RecoveryAction action = recoveryActionRepository.findByRazorpayReference(plinkId);
        log.info("[DIAGNOSTIC] RecoveryAction found for link {}: {}", plinkId, (action != null));
        
        if (action == null) {
            logAuditWithOutput(null, null, AuditLog.EventType.WEBHOOK_RECONCILIATION_FAILED, "Unknown payment link ID: " + plinkId, "{\"reason\":\"RECOVERY_ACTION_NOT_FOUND\"}", false);
            return false;
        }

        RecoveryCase rc = action.getRecoveryCase();
        log.info("[DIAGNOSTIC] RecoveryCase ID: {}, Status: {}, Action Status: {}", rc.getId(), rc.getStatus(), action.getStatus());
        
        switch (payload.event()) {
            case "payment_link.paid":
                return handlePaid(payload, action, rc);
            case "payment_link.partially_paid":
                return handlePartiallyPaid(payload, action, rc);
            case "payment_link.cancelled":
                return handleCancelled(payload, action, rc);
            case "payment_link.expired":
                return handleExpired(payload, action, rc);
            default:
                log.info("[DIAGNOSTIC] Ignored unknown payment_link event type: {}", payload.event());
                logAuditWithOutput(null, null, AuditLog.EventType.WEBHOOK_RECONCILIATION_FAILED, "Unknown payment_link event: " + payload.event(), "{\"reason\":\"UNKNOWN_EVENT\"}", false);
                return true;
        }
    }

    private boolean handlePaid(RazorpayPaymentLinkWebhookPayload payload, RecoveryAction action, RecoveryCase rc) {
        var plink = payload.payload().paymentLink().entity();
        
        BigDecimal expectedAmount = action.getAmount();
        BigDecimal receivedAmount = plink.amount() != null ? new BigDecimal(plink.amount()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        log.info("[DIAGNOSTIC] Amount check. Expected: {}, Received: {}", expectedAmount, receivedAmount);

        if (expectedAmount != null && expectedAmount.compareTo(receivedAmount) != 0) {
            logAuditWithOutput(rc, action, AuditLog.EventType.WEBHOOK_RECONCILIATION_FAILED, "Amount mismatch. Expected: " + expectedAmount + ", Received: " + receivedAmount, "{\"reason\":\"AMOUNT_MISMATCH\"}", false);
            return false;
        }

        if (rc.getStatus() == RecoveryCase.Status.RECOVERED) {
            log.info("[DIAGNOSTIC] Case already RECOVERED. Marking as successful.");
            return true;
        }
        
        Payment payment = rc.getPayment();
        if (payment != null) {
            payment.setStatus(Payment.PaymentStatus.CAPTURED);
            payment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        action.setStatus(RecoveryAction.Status.SUCCESS);
        action.setResult("Payment completed via webhook");
        recoveryActionRepository.save(action);

        rc.setStatus(RecoveryCase.Status.RECOVERED);
        rc.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        recoveryCaseRepository.save(rc);

        logAudit(rc, action, AuditLog.EventType.PAYMENT_LINK_PAID, "Payment link fully paid", true);
        logAudit(rc, action, AuditLog.EventType.RECOVERY_CASE_RECOVERED, "Recovery completed", true);
        
        return true;
    }

    private boolean handlePartiallyPaid(RazorpayPaymentLinkWebhookPayload payload, RecoveryAction action, RecoveryCase rc) {
        logAudit(rc, action, AuditLog.EventType.PAYMENT_LINK_PARTIALLY_PAID, "Payment link partially paid", true);
        return true;
    }

    private boolean handleCancelled(RazorpayPaymentLinkWebhookPayload payload, RecoveryAction action, RecoveryCase rc) {
        action.setStatus(RecoveryAction.Status.FAILED);
        action.setResult("Payment link cancelled");
        recoveryActionRepository.save(action);
        
        if (rc.getStatus() == RecoveryCase.Status.EXECUTING) {
            rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
            rc.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            recoveryCaseRepository.save(rc);
        }
        
        logAudit(rc, action, AuditLog.EventType.PAYMENT_LINK_CANCELLED, "Payment link cancelled", true);
        return true;
    }

    private boolean handleExpired(RazorpayPaymentLinkWebhookPayload payload, RecoveryAction action, RecoveryCase rc) {
        action.setStatus(RecoveryAction.Status.FAILED);
        action.setResult("Payment link expired");
        recoveryActionRepository.save(action);
        
        if (rc.getStatus() == RecoveryCase.Status.EXECUTING) {
            rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
            rc.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            recoveryCaseRepository.save(rc);
        }
        
        logAudit(rc, action, AuditLog.EventType.PAYMENT_LINK_EXPIRED, "Payment link expired", true);
        return true;
    }

    private void logAudit(RecoveryCase rc, RecoveryAction action, AuditLog.EventType type, String message, boolean success) {
        logAuditWithOutput(rc, action, type, message, null, success);
    }

    private void logAuditWithOutput(RecoveryCase rc, RecoveryAction action, AuditLog.EventType type, String message, String outputData, boolean success) {
        AuditLog log = new AuditLog();
        log.setRecoveryCase(rc);
        log.setRecoveryAction(action);
        log.setEventType(type);
        log.setActorType(AuditLog.ActorType.RAZORPAY);
        log.setMessage(message);
        log.setOutputData(outputData);
        log.setSuccess(success);
        log.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        auditLogRepository.save(log);
    }
}
