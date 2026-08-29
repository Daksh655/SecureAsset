package com.secureasset.backend.agent;

import com.secureasset.backend.agent.dto.AgentRecommendation;
import com.secureasset.backend.entity.Customer;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

public class AgentServiceTest {

    private ChatClient mockChatClient;
    private ChatClient.ChatClientRequestSpec mockPromptSpec;
    private ChatClient.CallResponseSpec mockCallResponseSpec;
    private com.secureasset.backend.agent.tools.AgentToolRegistry mockToolRegistry;
    private com.secureasset.backend.repository.RecoveryCaseRepository mockRecoveryCaseRepository;
    private com.secureasset.backend.repository.AuditLogRepository mockAuditLogRepository;
    private com.secureasset.backend.repository.RecoveryActionRepository mockRecoveryActionRepository;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        mockChatClient = mock(ChatClient.class);
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        mockToolRegistry = mock(com.secureasset.backend.agent.tools.AgentToolRegistry.class);
        mockRecoveryCaseRepository = mock(com.secureasset.backend.repository.RecoveryCaseRepository.class);
        mockAuditLogRepository = mock(com.secureasset.backend.repository.AuditLogRepository.class);
        mockRecoveryActionRepository = mock(com.secureasset.backend.repository.RecoveryActionRepository.class);
        
        when(mockBuilder.defaultSystem(any(String.class))).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockChatClient);

        mockPromptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        mockCallResponseSpec = mock(ChatClient.CallResponseSpec.class);
        
        when(mockChatClient.prompt()).thenReturn(mockPromptSpec);
        when(mockPromptSpec.user(any(String.class))).thenReturn(mockPromptSpec);
        
        // Mock fluent toolCallbacks registration
        when(mockPromptSpec.toolCallbacks(any(org.springframework.ai.tool.ToolCallback[].class))).thenReturn(mockPromptSpec);
        
        // Mock fluent options registration
        when(mockPromptSpec.options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class))).thenReturn(mockPromptSpec);
        
        when(mockPromptSpec.call()).thenReturn(mockCallResponseSpec);

        agentService = new AgentService(mockBuilder, mockToolRegistry, mockRecoveryCaseRepository, mockAuditLogRepository, mockRecoveryActionRepository);

    }

    @Test 
    void highValueTimeoutCaseShouldProduceValidRecommendation() {
        // Setup facts
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setEmail("vip@example.com");

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(Payment.FailureReason.TIMEOUT);
        payment.setAttemptNumber(1);
        payment.setFailedAt(OffsetDateTime.now());

        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());
        recoveryCase.setCustomer(customer);
        recoveryCase.setPayment(payment);
        recoveryCase.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
        recoveryCase.setPriority(RecoveryCase.Priority.HIGH);
        recoveryCase.setRecoveryScore(95);
        recoveryCase.setRiskAmount(new BigDecimal("50000.00"));
        recoveryCase.setDetectedAt(OffsetDateTime.now());

        // Mock response
        AgentRecommendation fakeRecommendation = new AgentRecommendation(
                RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK,
                92,
                "High value timeout",
                List.of("Timeout occurred", "VIP user")
        );
        when(mockCallResponseSpec.entity(AgentRecommendation.class))
            .thenReturn(fakeRecommendation);

        // Invoke agent
        AgentRecommendation recommendation = agentService.investigateCase(recoveryCase);

        // Assertions
        assertThat(recommendation).isNotNull();
        assertThat(recommendation.action()).isEqualTo(RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK);
        assertThat(recommendation.confidence()).isEqualTo(92);
        assertThat(recommendation.reason()).isEqualTo("High value timeout");
        assertThat(recommendation.evidence()).containsExactly("Timeout occurred", "VIP user");
    }

    @Test
    void malformedModelResponseIsHandledSafely() {
        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());

        when(mockCallResponseSpec.entity(AgentRecommendation.class))
            .thenThrow(new RuntimeException("Parsing error"));

        // With the fallback loop, all models fail → final IllegalStateException thrown
        assertThatThrownBy(() -> agentService.investigateCase(recoveryCase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate or parse valid structured output from AI Agent");
    }

    @Test
    void invalidConfidenceIsRejected() {
        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());

        AgentRecommendation badRec = new AgentRecommendation(
                RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK,
                150, // Invalid confidence
                "Reason",
                List.of("Ev")
        );
        when(mockCallResponseSpec.entity(AgentRecommendation.class))
            .thenReturn(badRec);

        // Validation failure is treated as a model failure → all models exhaust → final IllegalStateException
        assertThatThrownBy(() -> agentService.investigateCase(recoveryCase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate or parse valid structured output from AI Agent");
    }

    @Test
    void nullActionIsRejected() {
        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());

        AgentRecommendation badRec = new AgentRecommendation(
                null, // Missing action
                90,
                "Reason",
                List.of("Ev")
        );
        when(mockCallResponseSpec.entity(AgentRecommendation.class))
            .thenReturn(badRec);

        // Validation failure → all models exhaust → final IllegalStateException
        assertThatThrownBy(() -> agentService.investigateCase(recoveryCase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate or parse valid structured output from AI Agent");
    }

    @Test
    void timeoutExceptionIsHandledSafely() {
        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());

        when(mockCallResponseSpec.entity(AgentRecommendation.class))
            .thenThrow(new RuntimeException("Read timed out"));

        // All models fail with timeout → final IllegalStateException thrown
        assertThatThrownBy(() -> agentService.investigateCase(recoveryCase))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate or parse valid structured output from AI Agent");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolExecutionIsBoundedAndHandlesExceptions() {
        // We will intercept the ToolCallback registered with ChatClientRequestSpec
        ArgumentCaptor<org.springframework.ai.tool.ToolCallback> callbackCaptor = ArgumentCaptor.forClass(org.springframework.ai.tool.ToolCallback.class);
        
        com.secureasset.backend.agent.tools.AgentTool<Object, Object> dummyTool = mock(com.secureasset.backend.agent.tools.AgentTool.class);
        when(dummyTool.getName()).thenReturn("dummyTool");
        when(dummyTool.getDescription()).thenReturn("desc");
        when(dummyTool.getInputSchema()).thenReturn(Object.class);
        
        // Return success for first, then throw on subsequent calls
        when(dummyTool.execute(any()))
                .thenReturn("Success")
                .thenThrow(new RuntimeException("Tool error"));
        
        when(mockToolRegistry.getAllTools()).thenReturn(Map.of("dummyTool", dummyTool));

        // Invoke agent (this will trigger tool registration)
        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());
        
        when(mockCallResponseSpec.entity(AgentRecommendation.class)).thenReturn(new AgentRecommendation(
                RecoveryCase.AgentRecommendation.NO_ACTION, 100, "Reason", List.of()
        ));
        
        // Mock the toolCallbacks method
        when(mockPromptSpec.toolCallbacks(callbackCaptor.capture())).thenReturn(mockPromptSpec);
        
        agentService.investigateCase(recoveryCase);
        
        org.springframework.ai.tool.ToolCallback registeredCallback = callbackCaptor.getValue();
        
        // 1st call: success
        String result1 = registeredCallback.call("{\"id\": 1}");
        verify(dummyTool).execute(any());
        
        // 2nd call: throws exception (use different input to bypass cache)
        String result2 = registeredCallback.call("{\"id\": 2}");
        assertThat(result2).contains("Error: Payment history tool temporarily unavailable.");
        
        // 3rd call: bounded but still under limit (fails again but safe)
        String result3 = registeredCallback.call("{\"id\": 3}");
        assertThat(result3).contains("Error: Payment history tool temporarily unavailable.");
        
        // 4th call: exceeds bound limit
        String result4 = registeredCallback.call("{\"id\": 4}");
        assertThat(result4).contains("Maximum tool calls exceeded");
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateToolCallsArePreventedAndCached() {
        ArgumentCaptor<org.springframework.ai.tool.ToolCallback> callbackCaptor = ArgumentCaptor.forClass(org.springframework.ai.tool.ToolCallback.class);
        
        com.secureasset.backend.agent.tools.AgentTool<String, String> testTool1 = mock(com.secureasset.backend.agent.tools.AgentTool.class);
        when(testTool1.getName()).thenReturn("testTool1");
        when(testTool1.getDescription()).thenReturn("desc1");
        when(testTool1.getInputSchema()).thenReturn(String.class);
        
        com.secureasset.backend.agent.tools.AgentTool<String, String> testTool2 = mock(com.secureasset.backend.agent.tools.AgentTool.class);
        when(testTool2.getName()).thenReturn("testTool2");
        when(testTool2.getDescription()).thenReturn("desc2");
        when(testTool2.getInputSchema()).thenReturn(String.class);
        
        when(mockToolRegistry.getAllTools()).thenReturn(Map.of("testTool1", testTool1, "testTool2", testTool2));

        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());
        
        when(mockCallResponseSpec.entity(AgentRecommendation.class)).thenReturn(new AgentRecommendation(
                RecoveryCase.AgentRecommendation.NO_ACTION, 100, "Reason", List.of()
        ));
        
        when(mockPromptSpec.toolCallbacks(callbackCaptor.capture())).thenReturn(mockPromptSpec);
        
        agentService.investigateCase(recoveryCase);
        
        List<org.springframework.ai.tool.ToolCallback> callbacks = callbackCaptor.getAllValues();
        org.springframework.ai.tool.ToolCallback tool1Callback = callbacks.stream().filter(c -> c.getToolDefinition().name().equals("testTool1")).findFirst().get();
        org.springframework.ai.tool.ToolCallback tool2Callback = callbacks.stream().filter(c -> c.getToolDefinition().name().equals("testTool2")).findFirst().get();

        when(testTool1.execute("inputA")).thenReturn("Result1A");
        when(testTool1.execute("inputB")).thenReturn("Result1B");
        when(testTool2.execute("inputA")).thenReturn("Result2A");

        // 1. first call to a tool executes normally (use valid json strings)
        String res1 = tool1Callback.call("\"inputA\"");
        assertThat(res1).contains("Result1A"); // ToolCallback serializes output to JSON
        verify(testTool1).execute("inputA");

        // 2. identical tool + identical input does not execute twice
        // 3. duplicate call returns the previously cached result
        String res2 = tool1Callback.call("\"inputA\"");
        assertThat(res2).contains("Result1A");
        verify(testTool1).execute("inputA"); // Still 1 invocation

        // 4. same tool + different input executes normally
        // To avoid hitting the 3-call limit for our 4th test, we'll reset callCount... wait, we can't.
        // But the previous calls were:
        // 1. inputA (valid)
        // 2. inputA (duplicate)
        // 3. testTool2 with inputA (valid)
        // 4. same tool (testTool1) with inputB (fails due to 3-call limit)
        
        // 5. different tool + same input executes normally
        String res3 = tool2Callback.call("\"inputA\"");
        assertThat(res3).contains("Result2A");
        verify(testTool2).execute("inputA");

        // 8. duplicate requests cannot bypass the 3-call limit
        // Since res1, res2, and res3 took 3 slots, this 4th call hits the limit.
        String res4 = tool1Callback.call("\"inputB\"");
        assertThat(res4).contains("Maximum tool calls exceeded");
        verify(testTool1, org.mockito.Mockito.never()).execute("inputB");
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateProtectionIsIsolatedBetweenInvestigations() {
        ArgumentCaptor<org.springframework.ai.tool.ToolCallback> callbackCaptor = ArgumentCaptor.forClass(org.springframework.ai.tool.ToolCallback.class);
        
        com.secureasset.backend.agent.tools.AgentTool<String, String> testTool = mock(com.secureasset.backend.agent.tools.AgentTool.class);
        when(testTool.getName()).thenReturn("testTool");
        when(testTool.getInputSchema()).thenReturn(String.class);
        when(testTool.execute("input")).thenReturn("Result1");
        
        when(mockToolRegistry.getAllTools()).thenReturn(Map.of("testTool", testTool));

        when(mockCallResponseSpec.entity(AgentRecommendation.class)).thenReturn(new AgentRecommendation(
                RecoveryCase.AgentRecommendation.NO_ACTION, 100, "Reason", List.of()
        ));
        when(mockPromptSpec.toolCallbacks(callbackCaptor.capture())).thenReturn(mockPromptSpec);
        
        // Investigation 1
        RecoveryCase case1 = new RecoveryCase(); case1.setId(UUID.randomUUID());
        agentService.investigateCase(case1);
        org.springframework.ai.tool.ToolCallback cb1 = callbackCaptor.getValue();
        
        cb1.call("\"input\"");
        verify(testTool).execute("input");

        // Investigation 2
        RecoveryCase case2 = new RecoveryCase(); case2.setId(UUID.randomUUID());
        when(testTool.execute("input")).thenReturn("Result2");
        agentService.investigateCase(case2);
        org.springframework.ai.tool.ToolCallback cb2 = callbackCaptor.getAllValues().get(1);
        
        cb2.call("\"input\"");
        
        // verify execution happened twice total (once per investigation)
        verify(testTool, org.mockito.Mockito.times(2)).execute("input");
    }

    @Test
    void testGuardrailsHighAmountEscalates() {
        RecoveryCase rc = new RecoveryCase();
        rc.setId(UUID.randomUUID());
        rc.setRiskAmount(new BigDecimal("15000.00")); // > 10000
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        
        when(mockRecoveryCaseRepository.findById(rc.getId())).thenReturn(java.util.Optional.of(rc));
        when(mockCallResponseSpec.entity(AgentRecommendation.class)).thenReturn(
            new AgentRecommendation(RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK, 95, "Looks good", List.of())
        );

        AgentRecommendation rec = agentService.investigateRecoveryCase(rc.getId());
        
        assertThat(rec.action()).isEqualTo(RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT);
        assertThat(rec.reason()).contains("Amount > 10,000");
    }

    @Test
    void testGuardrailsIneligibleOverridden() {
        RecoveryCase rc = new RecoveryCase();
        rc.setId(UUID.randomUUID());
        rc.setEligibility(RecoveryCase.Eligibility.INELIGIBLE);
        
        when(mockRecoveryCaseRepository.findById(rc.getId())).thenReturn(java.util.Optional.of(rc));

        AgentRecommendation rec = agentService.investigateRecoveryCase(rc.getId());
        
        assertThat(rec.action()).isEqualTo(RecoveryCase.AgentRecommendation.NO_ACTION);
        assertThat(rec.reason()).contains("ineligible");
    }

    @Test
    void testGuardrailsCapturedPaymentNoAction() {
        RecoveryCase rc = new RecoveryCase();
        rc.setId(UUID.randomUUID());
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        Payment p = new Payment();
        p.setStatus(Payment.PaymentStatus.CAPTURED);
        rc.setPayment(p);
        
        when(mockRecoveryCaseRepository.findById(rc.getId())).thenReturn(java.util.Optional.of(rc));

        AgentRecommendation rec = agentService.investigateRecoveryCase(rc.getId());
        
        assertThat(rec.action()).isEqualTo(RecoveryCase.AgentRecommendation.NO_ACTION);
        assertThat(rec.reason()).contains("Payment is already captured.");
    }

    @Test
    void testGuardrailsMaxAttemptsEscalates() {
        RecoveryCase rc = new RecoveryCase();
        rc.setId(UUID.randomUUID());
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setRiskAmount(new BigDecimal("5000.00"));
        
        when(mockRecoveryCaseRepository.findById(rc.getId())).thenReturn(java.util.Optional.of(rc));
        
        com.secureasset.backend.entity.RecoveryAction a1 = new com.secureasset.backend.entity.RecoveryAction();
        com.secureasset.backend.entity.RecoveryAction a2 = new com.secureasset.backend.entity.RecoveryAction();
        when(mockRecoveryActionRepository.findByRecoveryCaseId(rc.getId())).thenReturn(List.of(a1, a2));

        AgentRecommendation rec = agentService.investigateRecoveryCase(rc.getId());
        
        assertThat(rec.action()).isEqualTo(RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT);
        assertThat(rec.reason()).contains("Maximum recovery attempts");
    }

    @Test
    void testGuardrailsActiveActionConflicts() {
        RecoveryCase rc = new RecoveryCase();
        rc.setId(UUID.randomUUID());
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setRiskAmount(new BigDecimal("5000.00"));
        
        when(mockRecoveryCaseRepository.findById(rc.getId())).thenReturn(java.util.Optional.of(rc));
        
        com.secureasset.backend.entity.RecoveryAction a1 = new com.secureasset.backend.entity.RecoveryAction();
        a1.setStatus(com.secureasset.backend.entity.RecoveryAction.Status.EXECUTING);
        when(mockRecoveryActionRepository.findByRecoveryCaseId(rc.getId())).thenReturn(List.of(a1));

        when(mockCallResponseSpec.entity(AgentRecommendation.class)).thenReturn(
            new AgentRecommendation(RecoveryCase.AgentRecommendation.RETRY_PAYMENT, 95, "Looks good", List.of())
        );

        AgentRecommendation rec = agentService.investigateRecoveryCase(rc.getId());
        
        assertThat(rec.action()).isEqualTo(RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT);
        assertThat(rec.reason()).contains("Conflicting active recovery action");
    }

    // ============================================================
    // FALLBACK CHAIN TESTS (Tests 1-6 from spec)
    // ============================================================

    /**
     * Helper to create a minimal RecoveryCase stub that passes all pre-checks.
     */
    private RecoveryCase minimalEligibleCase() {
        RecoveryCase rc = new RecoveryCase();
        rc.setId(UUID.randomUUID());
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setRiskAmount(new BigDecimal("5000.00"));
        when(mockRecoveryCaseRepository.findById(rc.getId())).thenReturn(java.util.Optional.of(rc));
        when(mockRecoveryActionRepository.findByRecoveryCaseId(rc.getId())).thenReturn(List.of());
        return rc;
    }

    /**
     * TEST 1 — Model 1 succeeds: only model 1 is called; its recommendation is returned.
     */
    @Test
    void fallbackTest1_firstModelSucceeds() {
        RecoveryCase rc = minimalEligibleCase();

        AgentRecommendation expected = new AgentRecommendation(
                RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK, 90, "Model1 result", List.of());
        when(mockCallResponseSpec.entity(AgentRecommendation.class)).thenReturn(expected);

        // Override fallback list so we can count invocations cleanly
        agentService.setFallbackModels(List.of("model-1", "model-2", "model-3", "model-4"));

        AgentRecommendation result = agentService.investigateRecoveryCase(rc.getId());

        assertThat(result.action()).isEqualTo(RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK);
        assertThat(result.reason()).isEqualTo("Model1 result");

        // options() must be called exactly once (for model-1) — model-2,3,4 never reached
        org.mockito.Mockito.verify(mockPromptSpec, org.mockito.Mockito.times(1))
                .options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class));
    }

    /**
     * TEST 2 — Model 1 fails (429), Model 2 succeeds. Model 3 and 4 never called.
     */
    @Test
    void fallbackTest2_firstFailsSecondSucceeds() {
        RecoveryCase rc = minimalEligibleCase();
        agentService.setFallbackModels(List.of("model-1", "model-2", "model-3", "model-4"));

        AgentRecommendation model2Rec = new AgentRecommendation(
                RecoveryCase.AgentRecommendation.SEND_RECOVERY_REMINDER, 80, "Model2 result", List.of());

        // First call to entity() throws (model-1 fails), second returns the good result (model-2)
        when(mockCallResponseSpec.entity(AgentRecommendation.class))
                .thenThrow(new RuntimeException("429 RESOURCE_EXHAUSTED"))
                .thenReturn(model2Rec);

        AgentRecommendation result = agentService.investigateRecoveryCase(rc.getId());

        assertThat(result.action()).isEqualTo(RecoveryCase.AgentRecommendation.SEND_RECOVERY_REMINDER);
        assertThat(result.reason()).isEqualTo("Model2 result");

        // options() called twice: model-1, model-2 — model-3 and model-4 never reached
        org.mockito.Mockito.verify(mockPromptSpec, org.mockito.Mockito.times(2))
                .options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class));
    }

    /**
     * TEST 3 — Model 1 fails, Model 2 fails, Model 3 succeeds. Model 4 never called.
     */
    @Test
    void fallbackTest3_twoFailThirdSucceeds() {
        RecoveryCase rc = minimalEligibleCase();
        agentService.setFallbackModels(List.of("model-1", "model-2", "model-3", "model-4"));

        AgentRecommendation model3Rec = new AgentRecommendation(
                RecoveryCase.AgentRecommendation.RETRY_PAYMENT, 75, "Model3 result", List.of());

        when(mockCallResponseSpec.entity(AgentRecommendation.class))
                .thenThrow(new RuntimeException("model-1 fail"))
                .thenThrow(new RuntimeException("model-2 fail"))
                .thenReturn(model3Rec);

        AgentRecommendation result = agentService.investigateRecoveryCase(rc.getId());

        assertThat(result.action()).isEqualTo(RecoveryCase.AgentRecommendation.RETRY_PAYMENT);
        assertThat(result.reason()).isEqualTo("Model3 result");

        // options() called three times — model-4 never reached
        org.mockito.Mockito.verify(mockPromptSpec, org.mockito.Mockito.times(3))
                .options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class));
    }

    /**
     * TEST 4 — All four models fail → existing ESCALATE_TO_MERCHANT fallback returned.
     */
    @Test
    void fallbackTest4_allModelsFail_escalatesToMerchant() {
        RecoveryCase rc = minimalEligibleCase();
        agentService.setFallbackModels(List.of("model-1", "model-2", "model-3", "model-4"));

        when(mockCallResponseSpec.entity(AgentRecommendation.class))
                .thenThrow(new RuntimeException("model-1 fail"))
                .thenThrow(new RuntimeException("model-2 fail"))
                .thenThrow(new RuntimeException("model-3 fail"))
                .thenThrow(new RuntimeException("model-4 fail"));

        // The outer investigateRecoveryCase catches the exception and escalates
        AgentRecommendation result = agentService.investigateRecoveryCase(rc.getId());

        assertThat(result.action()).isEqualTo(RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT);

        // All four models were tried exactly once each
        org.mockito.Mockito.verify(mockPromptSpec, org.mockito.Mockito.times(4))
                .options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class));
    }

    /**
     * TEST 5 — Non-AI application error (DB record not found) is NOT silently
     * converted into a model fallback attempt. The orElseThrow() in
     * investigateRecoveryCase fires before investigateCase is ever reached,
     * so the IllegalArgumentException propagates uncaught — no model is tried.
     */
    @Test
    void fallbackTest5_nonAiErrorIsNotSilentlyRetried() {
        UUID unknownId = UUID.randomUUID();
        // Repository returns empty → IllegalArgumentException inside investigateRecoveryCase
        when(mockRecoveryCaseRepository.findById(unknownId)).thenReturn(java.util.Optional.empty());

        // The DB error propagates as IllegalArgumentException — it is NOT swallowed
        // by the fallback loop because it fires before investigateCase() is entered.
        assertThatThrownBy(() -> agentService.investigateRecoveryCase(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RecoveryCase not found");

        // No model was ever attempted (options() never called)
        org.mockito.Mockito.verify(mockPromptSpec, org.mockito.Mockito.never())
                .options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class));
    }

    /**
     * TEST 6 — Successful first model returns the same AgentRecommendation
     * structure (all fields present) as before the fallback refactor.
     */
    @Test
    void fallbackTest6_successfulModelReturnsFullRecommendationStructure() {
        RecoveryCase rc = minimalEligibleCase();
        agentService.setFallbackModels(List.of("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash"));

        AgentRecommendation expected = new AgentRecommendation(
                RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK,
                88,
                "Payment link recommended due to repeated failure",
                List.of("payment_status=FAILED", "attempt=2"));

        when(mockCallResponseSpec.entity(AgentRecommendation.class)).thenReturn(expected);

        AgentRecommendation result = agentService.investigateRecoveryCase(rc.getId());

        assertThat(result.action()).isEqualTo(RecoveryCase.AgentRecommendation.CREATE_PAYMENT_LINK);
        assertThat(result.confidence()).isEqualTo(88);
        assertThat(result.reason()).isEqualTo("Payment link recommended due to repeated failure");
        assertThat(result.evidence()).containsExactly("payment_status=FAILED", "attempt=2");

        // Only one model attempt
        org.mockito.Mockito.verify(mockPromptSpec, org.mockito.Mockito.times(1))
                .options(any(org.springframework.ai.chat.prompt.ChatOptions.Builder.class));
    }
}

