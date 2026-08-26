package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.RazorpayPaymentStatusResult;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.integration.RazorpayPaymentService;
import com.secureasset.backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

public class GetRazorpayPaymentStatusToolTest {

    private RazorpayPaymentService razorpayPaymentService;
    private PaymentRepository paymentRepository;
    private GetRazorpayPaymentStatusTool tool;

    @BeforeEach
    void setUp() {
        razorpayPaymentService = mock(RazorpayPaymentService.class);
        paymentRepository = mock(PaymentRepository.class);
        tool = new GetRazorpayPaymentStatusTool(razorpayPaymentService, paymentRepository);
    }

    @Test
    void missingInputsThrowsException() {
        assertThatThrownBy(() -> tool.execute(new GetRazorpayPaymentStatusTool.Input(null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Must provide either");
    }

    @Test
    void usesRazorpayPaymentIdWhenProvided() {
        String payId = "pay_direct";
        RazorpayPaymentStatusResult mockResult = new RazorpayPaymentStatusResult(
                payId, null, "captured", BigDecimal.TEN, "INR", "upi", true, OffsetDateTime.now(), null, null, OffsetDateTime.now()
        );
        
        when(razorpayPaymentService.fetchPaymentDetails(payId, null)).thenReturn(Optional.of(mockResult));

        RazorpayPaymentStatusResult result = tool.execute(new GetRazorpayPaymentStatusTool.Input(payId, null));

        assertThat(result).isEqualTo(mockResult);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void looksUpPaymentIdWhenOnlyOrderIdProvided() {
        UUID orderId = UUID.randomUUID();
        String payId = "pay_looked_up";
        
        Payment p1 = new Payment();
        p1.setRazorpayPaymentId(payId);
        p1.setCreatedAt(OffsetDateTime.now());
        
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(p1));
        
        RazorpayPaymentStatusResult mockResult = new RazorpayPaymentStatusResult(
                payId, orderId, "failed", BigDecimal.TEN, "INR", "upi", false, OffsetDateTime.now(), "error", "code", OffsetDateTime.now()
        );
        
        when(razorpayPaymentService.fetchPaymentDetails(payId, orderId)).thenReturn(Optional.of(mockResult));

        RazorpayPaymentStatusResult result = tool.execute(new GetRazorpayPaymentStatusTool.Input(null, orderId));

        assertThat(result).isEqualTo(mockResult);
        verify(paymentRepository).findByOrderId(orderId);
        verify(razorpayPaymentService).fetchPaymentDetails(payId, orderId);
    }

    @Test
    void unknownPaymentThrowsSafeException() {
        when(razorpayPaymentService.fetchPaymentDetails("pay_unknown", null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.execute(new GetRazorpayPaymentStatusTool.Input("pay_unknown", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to fetch payment details");
    }
}
