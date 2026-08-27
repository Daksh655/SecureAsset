package com.secureasset.backend.demo;

import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.entity.Customer;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.CustomerRepository;
import com.secureasset.backend.repository.OrderRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import com.secureasset.backend.service.RecoveryActionExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class RealRazorpayPaymentLinkExecutionDemoTest {

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
    private RecoveryActionExecutionService executionService;

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_REAL_RAZORPAY_DEMO", matches = "true", disabledReason = "Real Razorpay demo is disabled unless RUN_REAL_RAZORPAY_DEMO=true")
    public void testDeterministicRazorpayExecution() {
        System.out.println("Starting Deterministic Real Razorpay Execution Demo...");
        
        // 1. Create Fixture Data
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);

        Customer customer = new Customer();
        customer.setName("Demo User");
        customer.setEmail("demo.user@example.com");
        customer.setPhone("+919876543210");
        customer.setCreatedAt(now.minusDays(2));
        customer.setUpdatedAt(now.minusDays(2));
        customer = customerRepository.save(customer);

        Order order = new Order();
        order.setCustomer(customer);
        order.setAmount(new BigDecimal("7500.00"));
        order.setCurrency("INR");
        order.setStatus(Order.OrderStatus.FAILED);
        order.setCreatedAt(now.minusDays(1));
        order.setUpdatedAt(now.minusDays(1));
        order = orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setCustomer(customer);
        payment.setAmount(new BigDecimal("7500.00"));
        payment.setCurrency("INR");
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setMethod("UPI");
        payment.setFailureReason(Payment.FailureReason.BANK_DECLINE);
        payment.setCreatedAt(now.minusHours(23));
        payment.setUpdatedAt(now.minusHours(23));
        payment = paymentRepository.save(payment);

        RecoveryCase rc = new RecoveryCase();
        rc.setCustomer(customer);
        rc.setOrder(order);
        rc.setPayment(payment);
        rc.setRiskAmount(new BigDecimal("7500.00"));
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setStatus(RecoveryCase.Status.NEW);
        rc.setPriority(RecoveryCase.Priority.HIGH);
        rc.setRecoveryScore(90);
        rc.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
        rc.setDetectedAt(now.minusHours(23));
        rc.setUpdatedAt(now.minusHours(23));
        rc = caseRepository.save(rc);

        // 2. Pre-execution checks
        assertThat(rc.getRiskAmount()).isLessThanOrEqualTo(new BigDecimal("10000.00"));
        assertThat(rc.getEligibility()).isEqualTo(RecoveryCase.Eligibility.ELIGIBLE);
        
        List<RecoveryAction> actions = actionRepository.findByRecoveryCaseId(rc.getId());
        assertThat(actions).isEmpty();
        
        List<Payment> orderPayments = paymentRepository.findByOrderId(order.getId());
        assertThat(orderPayments.stream().anyMatch(p -> p.getStatus() == Payment.PaymentStatus.CAPTURED)).isFalse();

        // 3. Propose CREATE_PAYMENT_LINK deterministically
        RecoveryAction action = executionService.proposeAction(
                rc.getId(),
                RecoveryAction.ActionType.CREATE_PAYMENT_LINK,
                rc.getRiskAmount()
        );

        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.PENDING);
        assertThat(action.getApprovalStatus()).isEqualTo(RecoveryAction.ApprovalStatus.PENDING);

        // 4. Approve Action
        executionService.approveActionByCase(rc.getId(), RecoveryAction.ActionType.CREATE_PAYMENT_LINK, rc.getRiskAmount());
        
        action = actionRepository.findById(action.getId()).get();
        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.APPROVED);

        // 5. Execute Action (calls Razorpay)
        System.out.println("Executing external API call to Razorpay (Test Mode)...");
        executionService.executeAction(action.getId());

        // 6. Verify Results
        action = actionRepository.findById(action.getId()).get();
        rc = caseRepository.findById(rc.getId()).get();

        System.out.println("--- EXECUTION RESULT ---");
        System.out.println("Recovery Action Status: " + action.getStatus());
        System.out.println("Razorpay Reference (Link ID): " + action.getRazorpayReference());
        System.out.println("Short URL: " + action.getResult());
        System.out.println("Case Status: " + rc.getStatus());

        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.SUCCESS);
        assertThat(action.getRazorpayReference()).isNotNull().startsWith("plink_");
        
        assertThat(rc.getStatus()).isEqualTo(RecoveryCase.Status.EXECUTING);
        assertThat(rc.getStatus()).isNotEqualTo(RecoveryCase.Status.RECOVERED);

        // 7. Verify Audit Logs
        List<AuditLog> logs = auditLogRepository.findByRecoveryCaseIdOrderByCreatedAtAsc(rc.getId());
        boolean hasRequest = logs.stream().anyMatch(l -> l.getEventType() == AuditLog.EventType.RAZORPAY_REQUEST);
        boolean hasResponse = logs.stream().anyMatch(l -> l.getEventType() == AuditLog.EventType.RAZORPAY_RESPONSE);

        assertThat(hasRequest).isTrue();
        assertThat(hasResponse).isTrue();
        
        // Assert AuditLog references the correctly persisted RecoveryAction
        final UUID finalActionId = action.getId();
        assertThat(logs.stream().allMatch(l -> l.getRecoveryAction() != null && l.getRecoveryAction().getId().equals(finalActionId))).isTrue();
        
        // 8. Verify No Transaction Rollback (Data persists)
        // Since we removed @Transactional from this test method,
        // the data is committed to PostgreSQL and will not be rolled back.
        boolean inTransaction = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
        assertThat(inTransaction).isFalse();

        System.out.println("Deterministic Execution Test completed successfully.");
    }
}
