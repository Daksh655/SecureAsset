package com.secureasset.backend.agent;

import com.secureasset.backend.agent.dto.AgentRecommendation;
import com.secureasset.backend.entity.RecoveryCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_REASON_LENGTH = 1000;
    private static final int MAX_EVIDENCE_ITEMS = 20;

    private final ChatClient chatClient;
    private final com.secureasset.backend.agent.tools.AgentToolRegistry toolRegistry;
    private final com.secureasset.backend.repository.RecoveryCaseRepository recoveryCaseRepository;
    private final com.secureasset.backend.repository.AuditLogRepository auditLogRepository;
    private final com.secureasset.backend.repository.RecoveryActionRepository recoveryActionRepository;

    public AgentService(
            ChatClient.Builder chatClientBuilder, 
            com.secureasset.backend.agent.tools.AgentToolRegistry toolRegistry,
            com.secureasset.backend.repository.RecoveryCaseRepository recoveryCaseRepository,
            com.secureasset.backend.repository.AuditLogRepository auditLogRepository,
            com.secureasset.backend.repository.RecoveryActionRepository recoveryActionRepository) {
        this.toolRegistry = toolRegistry;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.auditLogRepository = auditLogRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.chatClient = chatClientBuilder
                .defaultSystem(
                        "You are a SecureAsset AI Recovery Agent.\n" +
                        "Your responsibility is ONLY to:\n" +
                        "- interpret the context\n" +
                        "- diagnose the likely cause\n" +
                        "- recommend one bounded recovery action\n" +
                        "- explain the recommendation\n\n" +
                        "CRITICAL RULES:\n" +
                        "- financial eligibility is decided by backend code\n" +
                        "- recovery score is decided by backend code\n" +
                        "- policy limits are decided by backend code\n" +
                        "- the model cannot execute financial actions\n" +
                        "- the model may only recommend one of the allowed actions\n" +
                        "- the model must use only supplied facts\n" +
                        "- the model must not invent customer/payment information"
                )
                .build();
    }

    public AgentRecommendation investigateRecoveryCase(java.util.UUID recoveryCaseId) {
        RecoveryCase recoveryCase = recoveryCaseRepository.findById(recoveryCaseId)
                .orElseThrow(() -> new IllegalArgumentException("RecoveryCase not found"));

        logAudit(recoveryCase, com.secureasset.backend.entity.AuditLog.EventType.CASE_ANALYSIS_STARTED, "Investigation started");

        if (RecoveryCase.Eligibility.INELIGIBLE.equals(recoveryCase.getEligibility())) {
            log.info("Case {} is ineligible. Overriding recommendation to NO_ACTION.", recoveryCaseId);
            return overrideAndLog(recoveryCase, RecoveryCase.AgentRecommendation.NO_ACTION, "Case is ineligible for recovery.");
        }

        // Check if there is already a captured payment
        if (recoveryCase.getPayment() != null && com.secureasset.backend.entity.Payment.PaymentStatus.CAPTURED.equals(recoveryCase.getPayment().getStatus())) {
            return overrideAndLog(recoveryCase, RecoveryCase.AgentRecommendation.NO_ACTION, "Payment is already captured.");
        }

        java.util.List<com.secureasset.backend.entity.RecoveryAction> actions = recoveryActionRepository.findByRecoveryCaseId(recoveryCaseId);
        
        // Check max attempts limit
        long previousAttempts = actions.size();
        if (previousAttempts >= 2) {
            return overrideAndLog(recoveryCase, RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT, "Maximum recovery attempts reached.");
        }

        AgentRecommendation rawRecommendation;
        try {
            rawRecommendation = investigateCase(recoveryCase);
            logAudit(recoveryCase, com.secureasset.backend.entity.AuditLog.EventType.AGENT_RECOMMENDATION_CREATED, "Raw AI recommendation: " + rawRecommendation.action());
        } catch (Exception e) {
            logAudit(recoveryCase, com.secureasset.backend.entity.AuditLog.EventType.TOOL_FAILED, "AI Investigation failed: " + e.getMessage());
            return overrideAndLog(recoveryCase, RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT, "AI failed to produce a valid recommendation.");
        }

        AgentRecommendation finalRecommendation = applyPolicyGuardrails(recoveryCase, rawRecommendation, actions);
        logAudit(recoveryCase, com.secureasset.backend.entity.AuditLog.EventType.POLICY_CHECKED, "Final policy-validated recommendation: " + finalRecommendation.action());
        
        return finalRecommendation;
    }

    private AgentRecommendation overrideAndLog(RecoveryCase rc, RecoveryCase.AgentRecommendation forcedAction, String reason) {
        logAudit(rc, com.secureasset.backend.entity.AuditLog.EventType.POLICY_CHECKED, "Policy Override: " + reason);
        return new AgentRecommendation(forcedAction, 100, reason, java.util.List.of("Deterministic Policy"));
    }

    private AgentRecommendation applyPolicyGuardrails(RecoveryCase recoveryCase, AgentRecommendation rec, java.util.List<com.secureasset.backend.entity.RecoveryAction> actions) {
        RecoveryCase.AgentRecommendation action = rec.action();

        if (recoveryCase.getRiskAmount() != null && recoveryCase.getRiskAmount().compareTo(new java.math.BigDecimal("10000.00")) > 0) {
            return new AgentRecommendation(RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT, rec.confidence(), "Amount > 10,000 requires human review.", rec.evidence());
        }

        // Active recovery action conflict
        long activeActions = actions.stream()
                .filter(a -> a.getStatus() == com.secureasset.backend.entity.RecoveryAction.Status.PENDING || 
                             a.getStatus() == com.secureasset.backend.entity.RecoveryAction.Status.EXECUTING)
                .count();
        if (activeActions > 0) {
            return new AgentRecommendation(RecoveryCase.AgentRecommendation.ESCALATE_TO_MERCHANT, rec.confidence(), "Conflicting active recovery action exists.", rec.evidence());
        }

        // E. If the AI proposes an action that is not allowed by the current policy (We don't have a complex policy config yet, but let's check for unrecognized actions just in case).
        // For MVP, we trust the Action Enum.

        return rec;
    }

    private void logAudit(RecoveryCase rc, com.secureasset.backend.entity.AuditLog.EventType eventType, String message) {
        com.secureasset.backend.entity.AuditLog auditLog = new com.secureasset.backend.entity.AuditLog();
        auditLog.setRecoveryCase(rc);
        auditLog.setEventType(eventType);
        auditLog.setActorType(com.secureasset.backend.entity.AuditLog.ActorType.SYSTEM); // or AGENT
        auditLog.setMessage(message);
        auditLog.setSuccess(true);
        auditLog.setCreatedAt(java.time.OffsetDateTime.now());
        auditLogRepository.save(auditLog);
    }

    public AgentRecommendation investigateCase(RecoveryCase recoveryCase) {
        log.info("Request started: investigating caseId={}", recoveryCase.getId());
        
        Map<String, Object> facts = new HashMap<>();
        facts.put("caseId", recoveryCase.getId());
        facts.put("problemType", recoveryCase.getProblemType());
        facts.put("priority", recoveryCase.getPriority());
        facts.put("recoveryScore", recoveryCase.getRecoveryScore());
        facts.put("riskAmount", recoveryCase.getRiskAmount());
        facts.put("detectedAt", recoveryCase.getDetectedAt());
        facts.put("eligibility", recoveryCase.getEligibility());
        
        if (recoveryCase.getCustomer() != null) {
            facts.put("customerId", recoveryCase.getCustomer().getId());
            facts.put("customerEmail", recoveryCase.getCustomer().getEmail());
        }
        
        if (recoveryCase.getOrder() != null) {
            facts.put("orderId", recoveryCase.getOrder().getId());
            facts.put("orderStatus", recoveryCase.getOrder().getStatus());
            facts.put("orderAmount", recoveryCase.getOrder().getAmount());
        }
        
        if (recoveryCase.getPayment() != null) {
            facts.put("paymentId", recoveryCase.getPayment().getId());
            facts.put("paymentStatus", recoveryCase.getPayment().getStatus());
            facts.put("paymentFailureReason", recoveryCase.getPayment().getFailureReason());
            facts.put("paymentMethod", recoveryCase.getPayment().getMethod());
            facts.put("paymentAttemptNumber", recoveryCase.getPayment().getAttemptNumber());
            facts.put("paymentFailedAt", recoveryCase.getPayment().getFailedAt());
        }

        String prompt = "Investigate the following recovery case and provide a structured recommendation.\n" +
                "Context Facts:\n" + facts.toString();

        try {
            org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt().user(prompt);
            
            AtomicInteger toolCallCount = new AtomicInteger(0);
            Map<String, Object> toolCache = new HashMap<>();

            // Dynamically register bounded tools
            for (com.secureasset.backend.agent.tools.AgentTool<?, ?> tool : toolRegistry.getAllTools().values()) {
                promptSpec = registerBoundedTool(promptSpec, tool, toolCallCount, toolCache, recoveryCase);
            }

            AgentRecommendation recommendation = promptSpec
                    .call()
                    .entity(AgentRecommendation.class);

            if (recommendation == null || recommendation.action() == null) {
                throw new IllegalStateException("Missing structured recommendation action");
            }
            if (recommendation.confidence() == null || recommendation.confidence() < 0 || recommendation.confidence() > 100) {
                throw new IllegalStateException("Confidence out of bounds: " + recommendation.confidence());
            }
            if (recommendation.reason() != null && recommendation.reason().length() > MAX_REASON_LENGTH) {
                throw new IllegalStateException("Reason length exceeds maximum allowed");
            }
            if (recommendation.evidence() != null && recommendation.evidence().size() > MAX_EVIDENCE_ITEMS) {
                throw new IllegalStateException("Evidence items exceed maximum allowed");
            }

            log.info("Recommendation generated for caseId={}: action={}", recoveryCase.getId(), recommendation.action());
            return recommendation;
        } catch (IllegalStateException e) {
            log.error("Validation failure for caseId={}", recoveryCase.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Model failure for caseId={}", recoveryCase.getId(), e);
            throw new IllegalStateException("Failed to generate or parse valid structured output from AI Agent", e);
        }
    }

    private <I, O> org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec registerBoundedTool(
            org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec spec,
            com.secureasset.backend.agent.tools.AgentTool<I, O> tool,
            AtomicInteger callCount,
            Map<String, Object> toolCache,
            RecoveryCase recoveryCase) {
            
        org.springframework.ai.tool.ToolCallback callback = org.springframework.ai.tool.function.FunctionToolCallback.builder(
                tool.getName(),
                (I input) -> {
                    if (callCount.incrementAndGet() > 3) {
                        return (O) "Error: Maximum tool calls exceeded. You must provide a final recommendation now.";
                    }
                    
                    String normalizedInput = input == null ? "null" : input.toString();
                    String cacheKey = tool.getName() + ":" + normalizedInput;
                    
                    if (toolCache.containsKey(cacheKey)) {
                        log.info("Duplicate tool call detected for tool={} input={}. Returning cached result.", tool.getName(), normalizedInput);
                        Object cached = toolCache.get(cacheKey);
                        if (cached != null) {
                            return (O) cached;
                        } else {
                            return (O) "Error: Duplicate tool call blocked.";
                        }
                    }

                    try {
                        logAudit(recoveryCase, com.secureasset.backend.entity.AuditLog.EventType.TOOL_CALLED, "Executing tool: " + tool.getName() + " with input: " + normalizedInput);
                        O result = tool.execute(input);
                        toolCache.put(cacheKey, result);
                        return result;
                    } catch (Exception e) {
                        log.error("Tool execution failed for tool={}", tool.getName(), e);
                        logAudit(recoveryCase, com.secureasset.backend.entity.AuditLog.EventType.TOOL_FAILED, "Tool failed: " + tool.getName());
                        // We do not cache exceptions so that they don't permanently poison the cache if transient, 
                        // though a retry might fail again. To be safe, we just return the safe error.
                        return (O) "Error: Payment history tool temporarily unavailable.";
                    }
                }
        )
        .description(tool.getDescription())
        .inputType(tool.getInputSchema())
        .build();
        
        return spec.toolCallbacks(callback);
    }
}
