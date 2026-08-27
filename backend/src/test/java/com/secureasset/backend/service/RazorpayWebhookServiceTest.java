package com.secureasset.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.ProcessedWebhookEvent;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.ProcessedWebhookEventRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RazorpayWebhookServiceTest {

    private ProcessedWebhookEventRepository processedWebhookEventRepository;
    private RecoveryActionRepository recoveryActionRepository;
    private RecoveryCaseRepository recoveryCaseRepository;
    private AuditLogRepository auditLogRepository;
    private RazorpayWebhookService service;

    @BeforeEach
    void setUp() {
        processedWebhookEventRepository = mock(ProcessedWebhookEventRepository.class);
        recoveryActionRepository = mock(RecoveryActionRepository.class);
        recoveryCaseRepository = mock(RecoveryCaseRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);

        service = new RazorpayWebhookService(
                processedWebhookEventRepository,
                recoveryActionRepository,
                recoveryCaseRepository,
                auditLogRepository
        );
    }

    @Test
    void testDuplicateEventIgnored() {
        when(processedWebhookEventRepository.existsById("evt_123")).thenReturn(true);
        service.processWebhook("{}", "evt_123");
        verify(recoveryActionRepository, never()).findByRazorpayReference(anyString());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class)); // WEBHOOK_DUPLICATE_IGNORED
    }

    @Test
    void testMalformedPayload_EventRemainsRetryable() {
        service.processWebhook("invalid_json", "evt_123");
        verify(recoveryActionRepository, never()).findByRazorpayReference(anyString());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class)); // WEBHOOK_RECONCILIATION_FAILED
        verify(processedWebhookEventRepository, never()).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testUnknownEvent() {
        String payload = "{ \"event\": \"some_other_event\" }";
        service.processWebhook(payload, "evt_123");
        verify(recoveryActionRepository, never()).findByRazorpayReference(anyString());
        verify(auditLogRepository, times(2)).save(any(AuditLog.class));
        verify(processedWebhookEventRepository, times(1)).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testUnknownPaymentLinkId_EventRemainsRetryable() {
        String payload = """
                {
                  "event": "payment_link.paid",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_unknown"
                      }
                    }
                  }
                }
                """;
        when(recoveryActionRepository.findByRazorpayReference("plink_unknown")).thenReturn(null);

        service.processWebhook(payload, "evt_123");
        verify(recoveryActionRepository, times(1)).findByRazorpayReference("plink_unknown");
        verify(auditLogRepository, times(2)).save(any(AuditLog.class)); // RECEIVED + RECONCILIATION_FAILED
        verify(processedWebhookEventRepository, never()).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testAmountMismatch_EventRemainsRetryable() {
        String payload = """
                {
                  "event": "payment_link.paid",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_123",
                        "amount": 50000
                      }
                    }
                  }
                }
                """;

        RecoveryAction action = new RecoveryAction();
        action.setAmount(new BigDecimal("600.00")); // Mismatch: 500 received vs 600 expected
        RecoveryCase rc = new RecoveryCase();
        action.setRecoveryCase(rc);

        when(recoveryActionRepository.findByRazorpayReference("plink_123")).thenReturn(action);

        service.processWebhook(payload, "evt_123");
        verify(auditLogRepository, times(2)).save(any(AuditLog.class)); // RECEIVED + FAILED
        verify(processedWebhookEventRepository, never()).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testPaymentLinkPaidSuccess() {
        String payload = """
                {
                  "event": "payment_link.paid",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_123",
                        "amount": 750000
                      }
                    }
                  }
                }
                """;

        RecoveryAction action = new RecoveryAction();
        action.setAmount(new BigDecimal("7500.00"));
        RecoveryCase rc = new RecoveryCase();
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        Payment payment = new Payment();
        rc.setPayment(payment);
        action.setRecoveryCase(rc);

        when(recoveryActionRepository.findByRazorpayReference("plink_123")).thenReturn(action);

        service.processWebhook(payload, "evt_123");

        verify(recoveryActionRepository, times(1)).save(action);
        verify(recoveryCaseRepository, times(1)).save(rc);
        verify(auditLogRepository, times(3)).save(any(AuditLog.class)); // RECEIVED, PAID, RECOVERED
        verify(processedWebhookEventRepository, times(1)).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testRazorpayRetryAfterFailure() {
        // First attempt fails (unknown ID)
        String payload = """
                {
                  "event": "payment_link.paid",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_123",
                        "amount": 750000
                      }
                    }
                  }
                }
                """;
        when(recoveryActionRepository.findByRazorpayReference("plink_123")).thenReturn(null);

        service.processWebhook(payload, "evt_123");
        verify(processedWebhookEventRepository, never()).save(any(ProcessedWebhookEvent.class));

        // Fix underlying issue: Action is now found
        RecoveryAction action = new RecoveryAction();
        action.setAmount(new BigDecimal("7500.00"));
        RecoveryCase rc = new RecoveryCase();
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        action.setRecoveryCase(rc);
        when(recoveryActionRepository.findByRazorpayReference("plink_123")).thenReturn(action);

        service.processWebhook(payload, "evt_123");
        verify(processedWebhookEventRepository, times(1)).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testPaymentLinkPartiallyPaid() {
        String payload = """
                {
                  "event": "payment_link.partially_paid",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_123"
                      }
                    }
                  }
                }
                """;

        RecoveryAction action = new RecoveryAction();
        RecoveryCase rc = new RecoveryCase();
        action.setRecoveryCase(rc);

        when(recoveryActionRepository.findByRazorpayReference("plink_123")).thenReturn(action);

        service.processWebhook(payload, "evt_123");
        verify(recoveryActionRepository, never()).save(action);
        verify(recoveryCaseRepository, never()).save(rc);
        verify(processedWebhookEventRepository, times(1)).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testPaymentLinkCancelled() {
        String payload = """
                {
                  "event": "payment_link.cancelled",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_123"
                      }
                    }
                  }
                }
                """;

        RecoveryAction action = new RecoveryAction();
        RecoveryCase rc = new RecoveryCase();
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        action.setRecoveryCase(rc);

        when(recoveryActionRepository.findByRazorpayReference("plink_123")).thenReturn(action);

        service.processWebhook(payload, "evt_123");

        verify(recoveryActionRepository, times(1)).save(action);
        verify(recoveryCaseRepository, times(1)).save(rc);
        verify(processedWebhookEventRepository, times(1)).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testPaymentLinkExpired() {
        String payload = """
                {
                  "event": "payment_link.expired",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_123"
                      }
                    }
                  }
                }
                """;

        RecoveryAction action = new RecoveryAction();
        RecoveryCase rc = new RecoveryCase();
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        action.setRecoveryCase(rc);

        when(recoveryActionRepository.findByRazorpayReference("plink_123")).thenReturn(action);

        service.processWebhook(payload, "evt_123");

        verify(recoveryActionRepository, times(1)).save(action);
        verify(recoveryCaseRepository, times(1)).save(rc);
        verify(processedWebhookEventRepository, times(1)).save(any(ProcessedWebhookEvent.class));
    }
}
