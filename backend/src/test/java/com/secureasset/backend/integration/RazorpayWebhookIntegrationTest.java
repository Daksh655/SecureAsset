package com.secureasset.backend.integration;

import com.secureasset.backend.entity.Customer;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.CustomerRepository;
import com.secureasset.backend.repository.OrderRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.ProcessedWebhookEventRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import com.secureasset.backend.service.RazorpayWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class RazorpayWebhookIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RecoveryCaseRepository caseRepository;

    @Autowired
    private RecoveryActionRepository actionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ProcessedWebhookEventRepository processedWebhookEventRepository;

    @Autowired
    private RazorpayWebhookService webhookService;

    @Test
    @org.springframework.transaction.annotation.Transactional
    public void testEndToEndWebhookReconciliation() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Customer customer = new Customer();
        customer.setName("Webhook Test User");
        customer.setEmail("webhook@example.com");
        customer.setPhone("+919876543211");
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customer = customerRepository.save(customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setAmount(new BigDecimal("9900.00"));
        order.setCurrency("INR");
        order.setStatus(Order.OrderStatus.FAILED);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order = orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setCustomer(customer);
        payment.setAmount(new BigDecimal("9900.00"));
        payment.setCurrency("INR");
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(Payment.FailureReason.BANK_DECLINE);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment = paymentRepository.save(payment);

        RecoveryCase rc = new RecoveryCase();
        rc.setCustomer(customer);
        rc.setOrder(order);
        rc.setPayment(payment);
        rc.setRiskAmount(new BigDecimal("9900.00"));
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setStatus(RecoveryCase.Status.EXECUTING);
        rc.setPriority(RecoveryCase.Priority.HIGH);
        rc.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
        rc.setDetectedAt(now);
        rc.setUpdatedAt(now);
        rc = caseRepository.save(rc);

        RecoveryAction action = new RecoveryAction();
        action.setRecoveryCase(rc);
        action.setActionType(RecoveryAction.ActionType.CREATE_PAYMENT_LINK);
        action.setStatus(RecoveryAction.Status.EXECUTING);
        action.setAmount(new BigDecimal("9900.00"));
        action.setRazorpayReference("plink_webhook_demo");
        action.setRequestedAt(now);
        action = actionRepository.save(action);

        String payload = """
                {
                  "event": "payment_link.paid",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_webhook_demo",
                        "amount": 990000
                      }
                    }
                  }
                }
                """;

        String eventId = "evt_integration_123";

        // FIRST PROCESSING
        webhookService.processWebhook(payload, eventId);

        // Verify state changed
        action = actionRepository.findById(action.getId()).get();
        rc = caseRepository.findById(rc.getId()).get();

        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.SUCCESS);
        assertThat(rc.getStatus()).isEqualTo(RecoveryCase.Status.RECOVERED);
        assertThat(rc.getPayment().getStatus()).isEqualTo(Payment.PaymentStatus.CAPTURED);
        assertThat(processedWebhookEventRepository.existsById(eventId)).isTrue();

        long auditCountAfterFirst = auditLogRepository.findByRecoveryCaseIdOrderByCreatedAtAsc(rc.getId()).size();
        assertThat(auditCountAfterFirst).isGreaterThan(0);

        // SECOND PROCESSING (DUPLICATE)
        webhookService.processWebhook(payload, eventId);

        // Verify idempotency
        long auditCountAfterSecond = auditLogRepository.findByRecoveryCaseIdOrderByCreatedAtAsc(rc.getId()).size();
        // Since we pass rc=null for duplicate logs (as it's caught early), the count linked to rc shouldn't increase
        assertThat(auditCountAfterSecond).isEqualTo(auditCountAfterFirst);
    }
}
