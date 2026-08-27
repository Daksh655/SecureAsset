package com.secureasset.backend;

import com.secureasset.backend.entity.*;
import com.secureasset.backend.repository.*;
import com.secureasset.backend.service.DatasetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DatasetIntegrationTest {

    @Autowired private DatasetService datasetService;
    @Autowired private DemoDatasetRepository demoDatasetRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RecoveryCaseRepository recoveryCaseRepository;
    @Autowired private RecoveryActionRepository recoveryActionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private com.secureasset.backend.service.RevenueRiskEvaluationService evaluationService;

    @BeforeEach
    public void setup() {
        if (demoDatasetRepository.existsById(DatasetService.DEMO_DATASET_ID)) {
            try {
                datasetService.resetDataset();
            } catch (Exception e) {}
        }
        demoDatasetRepository.deleteAll();
    }

    @Test
    public void testDatasetLifecycle() {
        // 1. Generate Small
        datasetService.generateDataset("SMALL");
        assertTrue(demoDatasetRepository.existsById(DatasetService.DEMO_DATASET_ID));
        
        long customerCount = customerRepository.count();
        assertTrue(customerCount >= 50);

        // 5. active dataset prevents second generation
        assertThrows(IllegalStateException.class, () -> datasetService.generateDataset("SMALL"));

        // 13. active dataset required for recovery generation
        int generatedCases = evaluationService.evaluateDemoFailedPayments(DatasetService.DEMO_DATASET_ID);
        assertTrue(generatedCases > 0);
        
        long caseCount = recoveryCaseRepository.countByCustomerDatasetId(DatasetService.DEMO_DATASET_ID);
        assertTrue(caseCount > 0);

        // 15. no duplicate recovery cases
        evaluationService.evaluateDemoFailedPayments(DatasetService.DEMO_DATASET_ID);
        
        long newCaseCount = recoveryCaseRepository.countByCustomerDatasetId(DatasetService.DEMO_DATASET_ID);
        assertEquals(caseCount, newCaseCount);

        // Add some dummy non-demo data
        Customer nonDemoCustomer = new Customer();
        nonDemoCustomer.setId(java.util.UUID.randomUUID());
        nonDemoCustomer.setName("Real Customer");
        nonDemoCustomer.setRazorpayCustomerId("cust_real_" + System.currentTimeMillis());
        nonDemoCustomer.setEmail("real@real.com");
        nonDemoCustomer.setCreatedAt(java.time.OffsetDateTime.now());
        nonDemoCustomer.setUpdatedAt(java.time.OffsetDateTime.now());
        customerRepository.save(nonDemoCustomer);
        
        AuditLog nullLog = new AuditLog();
        nullLog.setCreatedAt(java.time.OffsetDateTime.now());
        nullLog.setMessage("Webhook received");
        nullLog.setSuccess(true);
        nullLog.setEventType(AuditLog.EventType.WEBHOOK_RECEIVED);
        auditLogRepository.save(nullLog);

        // 7. reset works
        datasetService.resetDataset();
        
        assertFalse(demoDatasetRepository.existsById(DatasetService.DEMO_DATASET_ID));
        assertEquals(0, customerRepository.countByDatasetId(DatasetService.DEMO_DATASET_ID));
        assertEquals(0, recoveryCaseRepository.countByCustomerDatasetId(DatasetService.DEMO_DATASET_ID));
        
        // 9. unrelated non-demo records survive
        assertTrue(customerRepository.existsById(nonDemoCustomer.getId()));
        
        // 10. null-linked audit logs survive
        assertTrue(auditLogRepository.existsById(nullLog.getId()));
    }

    @Test
    public void testConcurrentGenerationProtection() throws InterruptedException {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    datasetService.generateDataset("SMALL");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        while (!executor.isTerminated()) {}

        assertEquals(1, successCount.get(), "Only one generation should succeed");
        assertEquals(threads - 1, failCount.get(), "Others should fail");
    }

    @Test
    public void testInvalidSizeRejected() {
        assertThrows(IllegalArgumentException.class, () -> datasetService.generateDataset("INVALID"));
    }
}
