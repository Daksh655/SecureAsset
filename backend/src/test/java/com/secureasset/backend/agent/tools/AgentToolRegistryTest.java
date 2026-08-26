package com.secureasset.backend.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AgentToolRegistryTest {

    private GetCustomerPaymentHistoryTool mockHistoryTool;
    private GetOrderDetailsTool mockOrderTool;
    private GetRelatedRecoveryCasesTool mockRelatedCasesTool;
    private GetRecoveryPolicyTool mockPolicyTool;
    private GetRazorpayPaymentStatusTool mockStatusTool;
    private GetCustomerRecoveryProfileTool mockProfileTool;
    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        mockHistoryTool = mock(GetCustomerPaymentHistoryTool.class);
        mockOrderTool = mock(GetOrderDetailsTool.class);
        mockRelatedCasesTool = mock(GetRelatedRecoveryCasesTool.class);
        mockPolicyTool = mock(GetRecoveryPolicyTool.class);
        mockStatusTool = mock(GetRazorpayPaymentStatusTool.class);
        mockProfileTool = mock(GetCustomerRecoveryProfileTool.class);
        
        // Mock the required contract properties of the tool
        when(mockHistoryTool.getName()).thenReturn("getCustomerPaymentHistory");
        when(mockHistoryTool.getDescription()).thenReturn("Test description");

        when(mockOrderTool.getName()).thenReturn("getOrderDetails");
        when(mockOrderTool.getDescription()).thenReturn("Order description");

        when(mockRelatedCasesTool.getName()).thenReturn("getRelatedRecoveryCases");
        when(mockRelatedCasesTool.getDescription()).thenReturn("Related cases desc");

        when(mockPolicyTool.getName()).thenReturn("getRecoveryPolicy");
        when(mockPolicyTool.getDescription()).thenReturn("Policy desc");

        when(mockStatusTool.getName()).thenReturn("getRazorpayPaymentStatus");
        when(mockStatusTool.getDescription()).thenReturn("Status desc");

        when(mockProfileTool.getName()).thenReturn("getCustomerRecoveryProfile");
        when(mockProfileTool.getDescription()).thenReturn("Profile desc");

        registry = new AgentToolRegistry(mockHistoryTool, mockOrderTool, mockRelatedCasesTool, mockPolicyTool, mockStatusTool, mockProfileTool);
    }

    @Test
    void registeredToolCanBeFoundByExactName() {
        Optional<AgentTool<?, ?>> retrievedTool = registry.getTool("getCustomerPaymentHistory");
        
        assertThat(retrievedTool).isPresent();
        assertThat(retrievedTool.get()).isSameAs(mockHistoryTool);
    }

    @Test
    void unknownToolIsRejectedSafely() {
        Optional<AgentTool<?, ?>> retrievedTool = registry.getTool("someUnknownToolName");
        assertThat(retrievedTool).isEmpty();
    }

    @Test
    void nullOrBlankToolNameIsRejectedSafely() {
        assertThat(registry.getTool(null)).isEmpty();
        assertThat(registry.getTool("")).isEmpty();
        assertThat(registry.getTool("   ")).isEmpty();
    }
    
    @Test
    void toolMetadataIsPresent() {
        Optional<AgentTool<?, ?>> retrievedTool = registry.getTool("getCustomerPaymentHistory");
        
        assertThat(retrievedTool).isPresent();
        assertThat(retrievedTool.get().getName()).isEqualTo("getCustomerPaymentHistory");
        assertThat(retrievedTool.get().getDescription()).isEqualTo("Test description");
    }
    
    @Test
    void noArbitraryToolCanBeInjectedThroughModelProvidedName() {
        // Attempting to lookup generic components like the repository should fail
        Optional<AgentTool<?, ?>> databaseAccess = registry.getTool("paymentRepository");
        assertThat(databaseAccess).isEmpty();

        // Attempting to lookup random classes like java.lang.Runtime should fail
        Optional<AgentTool<?, ?>> randomClass = registry.getTool("java.lang.Runtime");
        assertThat(randomClass).isEmpty();
        
        // Ensure exactly 6 tools are registered
        assertThat(registry.getAllTools()).hasSize(6);
    }
}
