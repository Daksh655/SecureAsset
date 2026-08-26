package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.RecoveryPolicyResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class GetRecoveryPolicyTool implements AgentTool<GetRecoveryPolicyTool.Input, RecoveryPolicyResult> {

    public record Input() {}

    @Override
    public String getName() {
        return "getRecoveryPolicy";
    }

    @Override
    public String getDescription() {
        return "Retrieve the merchant's current recovery policy to understand allowed recovery actions and limits.";
    }

    @Override
    public Class<Input> getInputSchema() {
        return Input.class;
    }

    @Override
    public RecoveryPolicyResult execute(Input input) {
        return new RecoveryPolicyResult(
                2,
                new BigDecimal("10000.00"),
                true,
                true,
                List.of("TIMEOUT", "NETWORK_ERROR"),
                List.of("INSUFFICIENT_FUNDS", "BANK_DECLINE", "CUSTOMER_CANCELLED", "UNKNOWN"),
                24,
                new BigDecimal("10000.00")
        );
    }
}
