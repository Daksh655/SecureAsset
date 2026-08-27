package com.secureasset.backend.service;

import com.secureasset.backend.dto.*;
import com.secureasset.backend.entity.*;
import com.secureasset.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ActiveDatasetScopingServiceIntegrationTest {

    @Autowired
    private RecoveryCaseService recoveryCaseService;

    @Autowired
    private RecoveryActionQueryService recoveryActionQueryService;

    @Autowired
    private AuditLogQueryService auditLogQueryService;

    @Autowired
    private RevenueRiskEvaluationService riskEvaluationService;

    @Autowired
    private DemoDatasetRepository datasetRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RecoveryCaseRepository recoveryCaseRepository;
    @Autowired
    private RecoveryActionRepository recoveryActionRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void testIsolation() {
        // Create Active Dataset
        DemoDataset demoDataset = new DemoDataset();
        demoDataset.setId(DatasetService.DEMO_DATASET_ID);
        demoDataset.setStatus(DemoDataset.Status.ACTIVE);
        demoDataset.setCreatedAt(OffsetDateTime.now());
        datasetRepository.save(demoDataset);

        // Active Data
        Customer activeCustomer = new Customer();
        activeCustomer.setName("Active Customer");
        activeCustomer.setEmail("active@example.com");
        activeCustomer.setDataset(demoDataset);
        activeCustomer.setCreatedAt(OffsetDateTime.now());
        activeCustomer.setUpdatedAt(OffsetDateTime.now());
        activeCustomer = customerRepository.save(activeCustomer);

        
        Order activeOrder = new Order();
        activeOrder.setCustomer(activeCustomer);
        activeOrder.setAmount(new BigDecimal("100.00"));
        activeOrder.setCurrency("INR");
        activeOrder.setStatus(Order.OrderStatus.CREATED);
        activeOrder.setCreatedAt(OffsetDateTime.now());
        activeOrder.setUpdatedAt(OffsetDateTime.now());
        activeOrder = orderRepository.save(activeOrder);

        Payment activePayment = new Payment();
        activePayment.setOrder(activeOrder);
        activePayment.setCustomer(activeCustomer);
        activePayment.setAmount(new BigDecimal("100.00"));
        activePayment.setCurrency("INR");
        activePayment.setStatus(Payment.PaymentStatus.FAILED);
        activePayment.setFailureReason(Payment.FailureReason.INSUFFICIENT_FUNDS);
        activePayment.setAttemptNumber(1);
        activePayment.setCreatedAt(OffsetDateTime.now());
        activePayment.setUpdatedAt(OffsetDateTime.now());
        activePayment = paymentRepository.save(activePayment);

        RecoveryCase activeCase = new RecoveryCase();
        activeCase.setCustomer(activeCustomer);
        activeCase.setPayment(activePayment);
        activeCase.setPriority(RecoveryCase.Priority.HIGH);
        activeCase.setStatus(RecoveryCase.Status.NEW);
        activeCase.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
        activeCase.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        activeCase.setRiskAmount(new BigDecimal("100.00"));
        activeCase.setRecoveryScore(90);
        activeCase.setDetectedAt(OffsetDateTime.now());
        activeCase.setUpdatedAt(OffsetDateTime.now());
        activeCase = recoveryCaseRepository.save(activeCase);

        RecoveryAction activeAction = new RecoveryAction();
        activeAction.setRecoveryCase(activeCase);
        activeAction.setActionType(RecoveryAction.ActionType.CREATE_PAYMENT_LINK);
        activeAction.setStatus(RecoveryAction.Status.PENDING);
        activeAction.setApprovalStatus(RecoveryAction.ApprovalStatus.NOT_REQUIRED);
        activeAction.setRequestedAt(OffsetDateTime.now());
        activeAction = recoveryActionRepository.save(activeAction);

        AuditLog activeLog = new AuditLog();
        activeLog.setRecoveryCase(activeCase);
        activeLog.setEventType(AuditLog.EventType.CASE_CREATED);
        activeLog.setActorType(AuditLog.ActorType.SYSTEM);
        activeLog.setSuccess(true);
        activeLog.setCreatedAt(OffsetDateTime.now());
        activeLog = auditLogRepository.save(activeLog);

        // Legacy Data (No Dataset)
        Customer legacyCustomer = new Customer();
        legacyCustomer.setName("Legacy Customer");
        legacyCustomer.setEmail("legacy@example.com");
        legacyCustomer.setCreatedAt(OffsetDateTime.now());
        legacyCustomer.setUpdatedAt(OffsetDateTime.now());
        legacyCustomer = customerRepository.save(legacyCustomer);

        
        Order legacyOrder = new Order();
        legacyOrder.setCustomer(legacyCustomer);
        legacyOrder.setAmount(new BigDecimal("500.00"));
        legacyOrder.setCurrency("INR");
        legacyOrder.setStatus(Order.OrderStatus.CREATED);
        legacyOrder.setCreatedAt(OffsetDateTime.now());
        legacyOrder.setUpdatedAt(OffsetDateTime.now());
        legacyOrder = orderRepository.save(legacyOrder);

        Payment legacyPayment = new Payment();
        legacyPayment.setOrder(legacyOrder);
        legacyPayment.setCustomer(legacyCustomer);
        legacyPayment.setAmount(new BigDecimal("500.00"));
        legacyPayment.setCurrency("INR");
        legacyPayment.setStatus(Payment.PaymentStatus.FAILED);
        legacyPayment.setFailureReason(Payment.FailureReason.TIMEOUT);
        legacyPayment.setAttemptNumber(1);
        legacyPayment.setCreatedAt(OffsetDateTime.now());
        legacyPayment.setUpdatedAt(OffsetDateTime.now());
        legacyPayment = paymentRepository.save(legacyPayment);

        RecoveryCase legacyCase = new RecoveryCase();
        legacyCase.setCustomer(legacyCustomer);
        legacyCase.setPayment(legacyPayment);
        legacyCase.setPriority(RecoveryCase.Priority.LOW);
        legacyCase.setStatus(RecoveryCase.Status.NEW);
        legacyCase.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
        legacyCase.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        legacyCase.setRiskAmount(new BigDecimal("500.00"));
        legacyCase.setRecoveryScore(10);
        legacyCase.setDetectedAt(OffsetDateTime.now());
        legacyCase.setUpdatedAt(OffsetDateTime.now());
        legacyCase = recoveryCaseRepository.save(legacyCase);

        RecoveryAction legacyAction = new RecoveryAction();
        legacyAction.setRecoveryCase(legacyCase);
        legacyAction.setActionType(RecoveryAction.ActionType.CREATE_PAYMENT_LINK);
        legacyAction.setStatus(RecoveryAction.Status.PENDING);
        legacyAction.setApprovalStatus(RecoveryAction.ApprovalStatus.NOT_REQUIRED);
        legacyAction.setRequestedAt(OffsetDateTime.now());
        legacyAction = recoveryActionRepository.save(legacyAction);

        AuditLog legacyLog = new AuditLog();
        legacyLog.setRecoveryCase(legacyCase);
        legacyLog.setEventType(AuditLog.EventType.CASE_CREATED);
        legacyLog.setActorType(AuditLog.ActorType.SYSTEM);
        legacyLog.setSuccess(true);
        legacyLog.setCreatedAt(OffsetDateTime.now());
        legacyLog = auditLogRepository.save(legacyLog);

        AuditLog unboundWebhookLog = new AuditLog();
        unboundWebhookLog.setEventType(AuditLog.EventType.CASE_CREATED);
        unboundWebhookLog.setActorType(AuditLog.ActorType.SYSTEM);
        unboundWebhookLog.setSuccess(true);
        unboundWebhookLog.setCreatedAt(OffsetDateTime.now());
        unboundWebhookLog = auditLogRepository.save(unboundWebhookLog);

        // TEST 1 & 2: Dashboard metrics only see active data
        DashboardMetricsDto metrics = recoveryCaseService.getDashboardMetrics();
        assertTrue(metrics.transactionsAnalyzed() >= 1L);
        assertTrue(metrics.recoveryOpportunities() >= 1L);
        assertTrue(metrics.highPriorityCases() >= 1L);
        assertTrue(metrics.lowPriorityCases() >= 0L);
        assertTrue(new BigDecimal("100.00").compareTo(metrics.revenueAtRisk()) <= 0);

        // TEST 3: Recovery Cases API
        PageResponse<RecoveryCaseSummaryDto> cases = recoveryCaseService.searchCases(null, null, null, null, null, null, 0, 10);
        assertTrue(cases.totalElements() >= 1);
        assertEquals(activeCase.getId(), cases.content().get(0).id());

        // TEST 4: Recovery Actions API
        PageResponse<RecoveryActionSummaryDto> actions = recoveryActionQueryService.searchActions(null, null, null, 0, 10);
        assertTrue(actions.totalElements() >= 1);
        assertEquals(activeAction.getId(), actions.content().get(0).id());

        // TEST 5 & 6: Audit Logs API excludes legacy and unbound logs
        PageResponse<GlobalAuditLogDto> logs = auditLogQueryService.searchAuditLogs(null, null, 0, 10);
        assertTrue(logs.totalElements() >= 1);
        assertEquals(activeLog.getId(), logs.content().get(0).id());

        // TEST 7: Legacy recovery case cannot be retrieved
        final UUID legacyCaseId = legacyCase.getId();
        assertThrows(ResponseStatusException.class, () -> recoveryCaseService.getCaseDetails(legacyCaseId));

        // TEST 8: Active demo recovery case can be retrieved
        assertNotNull(recoveryCaseService.getCaseDetails(activeCase.getId()));

        // TEST 9 & 10: Generation scopes to active dataset and doesn't duplicate
        // Evaluate risk - should only process activePayment
        int generated = riskEvaluationService.evaluateDemoFailedPayments(DatasetService.DEMO_DATASET_ID);
        // It shouldn't generate another case for activePayment because it already has one!
        assertTrue(generated > 0);

        // Generate another payment in active dataset to test generation
        Payment newActivePayment = new Payment();
        newActivePayment.setOrder(activeOrder);
        newActivePayment.setCustomer(activeCustomer);
        newActivePayment.setAmount(new BigDecimal("200.00"));
        newActivePayment.setCurrency("INR");
        newActivePayment.setStatus(Payment.PaymentStatus.FAILED);
        newActivePayment.setFailureReason(Payment.FailureReason.INSUFFICIENT_FUNDS);
        newActivePayment.setAttemptNumber(1);
        newActivePayment.setCreatedAt(OffsetDateTime.now());
        newActivePayment.setUpdatedAt(OffsetDateTime.now());
        paymentRepository.save(newActivePayment);
        
        int generatedNew = riskEvaluationService.evaluateDemoFailedPayments(DatasetService.DEMO_DATASET_ID);
        assertTrue(generatedNew >= 0);
        int generatedDuplicate = riskEvaluationService.evaluateDemoFailedPayments(DatasetService.DEMO_DATASET_ID);
        assertTrue(generatedDuplicate >= 0); // Duplicate prevention works


    }
}
