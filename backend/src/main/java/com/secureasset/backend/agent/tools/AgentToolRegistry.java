package com.secureasset.backend.agent.tools;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool<?, ?>> tools = new ConcurrentHashMap<>();

    public AgentToolRegistry(
            GetCustomerPaymentHistoryTool getCustomerPaymentHistoryTool,
            GetOrderDetailsTool getOrderDetailsTool,
            GetRelatedRecoveryCasesTool getRelatedRecoveryCasesTool,
            GetRecoveryPolicyTool getRecoveryPolicyTool,
            GetRazorpayPaymentStatusTool getRazorpayPaymentStatusTool,
            GetCustomerRecoveryProfileTool getCustomerRecoveryProfileTool) {
        // Register explicitly and safely by exact name.
        tools.put(getCustomerPaymentHistoryTool.getName(), getCustomerPaymentHistoryTool);
        tools.put(getOrderDetailsTool.getName(), getOrderDetailsTool);
        tools.put(getRelatedRecoveryCasesTool.getName(), getRelatedRecoveryCasesTool);
        tools.put(getRecoveryPolicyTool.getName(), getRecoveryPolicyTool);
        tools.put(getRazorpayPaymentStatusTool.getName(), getRazorpayPaymentStatusTool);
        tools.put(getCustomerRecoveryProfileTool.getName(), getCustomerRecoveryProfileTool);
    }

    /**
     * Look up a tool by its exact registered name.
     * 
     * @param toolName The exact name of the tool
     * @return An Optional containing the tool if it exists and is registered, otherwise empty.
     */
    public Optional<AgentTool<?, ?>> getTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(toolName));
    }

    /**
     * Returns a read-only view of all registered tools.
     * @return Map of registered tools keyed by tool name.
     */
    public Map<String, AgentTool<?, ?>> getAllTools() {
        return Collections.unmodifiableMap(tools);
    }
}
