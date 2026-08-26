package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.RecoveryPolicyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class GetRecoveryPolicyToolTest {

    private GetRecoveryPolicyTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetRecoveryPolicyTool();
    }

    @Test
    void policyReturnsCorrectDeterministicValues() {
        RecoveryPolicyResult result = tool.execute(new GetRecoveryPolicyTool.Input());

        assertThat(result).isNotNull();
        
        // maximum amount is ₹10,000
        assertThat(result.maxAutoRecoveryAmount()).isEqualTo(new BigDecimal("10000.00"));
        
        // human approval threshold is ₹10,000
        assertThat(result.humanApprovalRequiredAboveAmount()).isEqualTo(new BigDecimal("10000.00"));
        
        // retry allowed only for the configured failure reasons
        assertThat(result.retryAllowedFailureReasons()).containsExactlyInAnyOrder("TIMEOUT", "NETWORK_ERROR");
        assertThat(result.retryDisallowedFailureReasons()).containsExactlyInAnyOrder(
                "INSUFFICIENT_FUNDS", "BANK_DECLINE", "CUSTOMER_CANCELLED", "UNKNOWN");
                
        // payment link allowed
        assertThat(result.paymentLinkAllowed()).isTrue();
        
        // verify other values
        assertThat(result.maxRecoveryAttempts()).isEqualTo(2);
        assertThat(result.retryPaymentAllowed()).isTrue();
        assertThat(result.cooldownHours()).isEqualTo(24);
    }
}
