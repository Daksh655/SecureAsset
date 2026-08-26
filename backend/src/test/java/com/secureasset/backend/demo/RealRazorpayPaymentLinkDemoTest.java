package com.secureasset.backend.demo;

import com.secureasset.backend.agent.AgentService;
import com.secureasset.backend.agent.dto.AgentRecommendation;
import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import com.secureasset.backend.service.RecoveryActionExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
public class RealRazorpayPaymentLinkDemoTest {

    @Autowired
    private RecoveryCaseRepository caseRepository;

    @Autowired
    private RecoveryActionRepository actionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AgentService agentService;

    @Autowired
    private RecoveryActionExecutionService executionService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @org.springframework.transaction.annotation.Transactional
    @EnabledIfEnvironmentVariable(named = "RUN_REAL_RAZORPAY_DEMO", matches = "true")
    public void executeRealAiInvestigationDemo() {
        System.out.println("Starting real Razorpay end-to-end demo execution...");

        System.out.println("Finding a suitable demo case from DB...");
        
        List<RecoveryCase> allCases = caseRepository.findAll();
        RecoveryCase selectedCase = null;
        int maxScore = -1;

        // We just need ANY valid eligible case for the AI demo
        for (RecoveryCase candidate : allCases) {
            if (candidate.getEligibility() == RecoveryCase.Eligibility.ELIGIBLE &&
                (candidate.getStatus() == RecoveryCase.Status.NEW || candidate.getStatus() == RecoveryCase.Status.ACTION_REQUIRED)) {
                selectedCase = candidate;
                break;
            }
        }

        if (selectedCase == null) {
            org.junit.jupiter.api.Assertions.fail("Could not find any eligible test case in the database.");
        }

        UUID demoCaseId = selectedCase.getId();
        RecoveryCase rc = selectedCase;

        // 3. Run AgentService
        System.out.println("--- AI INVESTIGATION DEMO ---");
        System.out.println("Recovery Case ID: " + rc.getId());
        System.out.println("Amount: " + rc.getRiskAmount());
        System.out.println("Failure Reason: " + (rc.getPayment() != null ? rc.getPayment().getFailureReason() : "N/A"));
        System.out.println("Score: " + rc.getRecoveryScore());
        System.out.println("Priority: " + rc.getPriority());
        System.out.println("Running AI agent investigation...");
        
        AgentRecommendation recommendation = agentService.investigateCase(rc);

        System.out.println("--- AGENT RECOMMENDATION ---");
        System.out.println("Action Type: " + recommendation.action());
        System.out.println("Confidence: " + recommendation.confidence());
        System.out.println("Rationale: " + recommendation.reason());

        System.out.println("AI investigation demo complete. Razorpay execution is handled in RealRazorpayPaymentLinkExecutionDemoTest.");
    }
}
