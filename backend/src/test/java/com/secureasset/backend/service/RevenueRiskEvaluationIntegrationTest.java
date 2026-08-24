package com.secureasset.backend.service;

import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RevenueRiskEvaluationIntegrationTest {

    @Autowired
    private RevenueRiskEvaluationService evaluationService;

    @Autowired
    private RecoveryCaseRepository recoveryCaseRepository;

    @Test
    public void testEvaluateFailedPaymentsIntegration() {
        // 3. Invoke evaluateFailedPayments() without @Transactional to persist the results
        assertDoesNotThrow(() -> evaluationService.evaluateFailedPayments());

        // 5. Use RecoveryCaseRepository to verify that recovery cases were actually created
        List<RecoveryCase> cases = recoveryCaseRepository.findAll();
        
        // Assertions requested
        assertFalse(cases.isEmpty(), "Recovery cases should exist when eligible failed payments exist");

        for (RecoveryCase rc : cases) {
            assertTrue(rc.getRecoveryScore() >= 0 && rc.getRecoveryScore() <= 100, 
                    "Score must be between 0 and 100");
            assertNotNull(rc.getPriority(), "Priority must be valid");
            assertEquals(RecoveryCase.Eligibility.ELIGIBLE, rc.getEligibility(), 
                    "Only eligible cases should be created");
        }

        // 6. Print a concise summary
        Map<RecoveryCase.Priority, Long> counts = cases.stream()
                .collect(Collectors.groupingBy(RecoveryCase::getPriority, Collectors.counting()));

        long high = counts.getOrDefault(RecoveryCase.Priority.HIGH, 0L);
        long medium = counts.getOrDefault(RecoveryCase.Priority.MEDIUM, 0L);
        long low = counts.getOrDefault(RecoveryCase.Priority.LOW, 0L);

        System.out.println("================================");
        System.out.println("--- Integration Test Summary ---");
        System.out.println("Total recovery cases: " + cases.size());
        System.out.println("HIGH priority: " + high);
        System.out.println("MEDIUM priority: " + medium);
        System.out.println("LOW priority: " + low);
        System.out.println("================================");
    }
}
