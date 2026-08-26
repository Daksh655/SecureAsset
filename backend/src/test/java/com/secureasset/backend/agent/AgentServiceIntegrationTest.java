package com.secureasset.backend.agent;

import com.secureasset.backend.agent.dto.AgentRecommendation;
import com.secureasset.backend.entity.Customer;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.repository.CustomerRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import com.secureasset.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AgentServiceIntegrationTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private RecoveryCaseRepository recoveryCaseRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private ChatClient chatClient = org.mockito.Mockito.mock(ChatClient.class);
    private ChatClient.ChatClientRequestSpec promptSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
    private ChatClient.CallResponseSpec callResponseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);

    @Test
    void testEndToEndInvestigationWithRealDbAndMockedGemini() {
        // Inject mocked chatClient into the real AgentService
        org.springframework.test.util.ReflectionTestUtils.setField(agentService, "chatClient", chatClient);
        
        // Mock ChatClient fluent API
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(any(String.class))).thenReturn(promptSpec);
        when(promptSpec.toolCallbacks(any(org.springframework.ai.tool.ToolCallback[].class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callResponseSpec);
        
        // Mock the final returned AgentRecommendation
        AgentRecommendation fakeRec = new AgentRecommendation(
                RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK,
                95,
                "Looks good, AI recommends this",
                List.of("Mocked DB evidence")
        );
        when(callResponseSpec.entity(AgentRecommendation.class)).thenReturn(fakeRec);

        // Create actual entities in test DB
        Customer c = new Customer();
        c.setEmail("integration@example.com");
        c.setName("Int Test");
        c.setPhone("+1234567890");
        c.setCreatedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        customerRepository.save(c);

        RecoveryCase rc = new RecoveryCase();
        rc.setCustomer(c);
        rc.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
        rc.setPriority(RecoveryCase.Priority.HIGH);
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setRiskAmount(new BigDecimal("500.00")); // Safe amount under 10k
        rc.setRecoveryScore(99);
        rc.setDetectedAt(OffsetDateTime.now());
        rc.setUpdatedAt(OffsetDateTime.now());
        rc.setStatus(RecoveryCase.Status.NEW);
        rc.setAgentStatus(RecoveryCase.AgentStatus.NOT_ANALYZED);
        recoveryCaseRepository.save(rc);

        // Execute the service
        AgentRecommendation recommendation = agentService.investigateRecoveryCase(rc.getId());

        // Verify that Audit Logs were created in the real database
        List<AuditLog> logs = auditLogRepository.findByRecoveryCaseIdOrderByCreatedAtAsc(rc.getId());
        for (AuditLog log : logs) {
            System.out.println("AUDIT LOG: " + log.getEventType() + " - " + log.getMessage());
        }
        
        assertThat(recommendation).isNotNull();
        assertThat(recommendation.action()).isEqualTo(RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK);
        assertThat(recommendation.confidence()).isEqualTo(95);

        assertThat(logs).isNotEmpty();
        assertThat(logs).anyMatch(l -> l.getEventType() == AuditLog.EventType.CASE_ANALYSIS_STARTED);
        assertThat(logs).anyMatch(l -> l.getEventType() == AuditLog.EventType.POLICY_CHECKED);
    }
}
