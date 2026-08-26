package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.OrderDetailsResult;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.repository.OrderRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetOrderDetailsToolTest {

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private GetOrderDetailsTool tool;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        tool = new GetOrderDetailsTool(orderRepository, paymentRepository);
    }

    @Test
    void missingOrderThrowsException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.execute(new GetOrderDetailsTool.Input(orderId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found with id: " + orderId);
    }

    @Test
    void validOrderWithNoPayments() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setAmount(new BigDecimal("500.00"));
        order.setCurrency("INR");
        order.setStatus(Order.OrderStatus.CREATED);
        order.setCreatedAt(OffsetDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Collections.emptyList());

        OrderDetailsResult result = tool.execute(new GetOrderDetailsTool.Input(orderId));

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.amount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(result.orderStatus()).isEqualTo("CREATED");
        assertThat(result.paymentAttempts()).isEqualTo(0);
        assertThat(result.latestPaymentStatus()).isNull();
    }

    @Test
    void validOrderWithMultipleAttempts() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setAmount(new BigDecimal("1200.00"));
        order.setCurrency("INR");
        order.setStatus(Order.OrderStatus.PAID);
        order.setCreatedAt(OffsetDateTime.now().minusHours(2));

        Payment failedPayment = new Payment();
        failedPayment.setStatus(Payment.PaymentStatus.FAILED);
        failedPayment.setFailureReason(Payment.FailureReason.INSUFFICIENT_FUNDS);
        failedPayment.setCreatedAt(OffsetDateTime.now().minusHours(1));

        Payment successPayment = new Payment();
        successPayment.setStatus(Payment.PaymentStatus.CAPTURED);
        successPayment.setCreatedAt(OffsetDateTime.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(failedPayment, successPayment));

        OrderDetailsResult result = tool.execute(new GetOrderDetailsTool.Input(orderId));

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.paymentAttempts()).isEqualTo(2);
        assertThat(result.successfulPaymentCount()).isEqualTo(1);
        assertThat(result.failedPaymentCount()).isEqualTo(1);
        assertThat(result.latestPaymentStatus()).isEqualTo("CAPTURED");
        assertThat(result.latestFailureReason()).isNull();
        assertThat(result.latestPaymentTimestamp()).isEqualTo(successPayment.getCreatedAt());
    }
    
    @Test
    void latestFailureInformationIsCorrectlyReturned() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setAmount(new BigDecimal("800.00"));
        order.setCurrency("INR");
        order.setStatus(Order.OrderStatus.FAILED);
        order.setCreatedAt(OffsetDateTime.now().minusHours(3));

        Payment oldSuccess = new Payment();
        oldSuccess.setStatus(Payment.PaymentStatus.CAPTURED); // Unlikely but good for testing ordering
        oldSuccess.setCreatedAt(OffsetDateTime.now().minusHours(2));

        Payment newFailure = new Payment();
        newFailure.setStatus(Payment.PaymentStatus.FAILED);
        newFailure.setFailureReason(Payment.FailureReason.BANK_DECLINE);
        newFailure.setCreatedAt(OffsetDateTime.now().minusHours(1));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(List.of(oldSuccess, newFailure));

        OrderDetailsResult result = tool.execute(new GetOrderDetailsTool.Input(orderId));

        assertThat(result.paymentAttempts()).isEqualTo(2);
        assertThat(result.latestPaymentStatus()).isEqualTo("FAILED");
        assertThat(result.latestFailureReason()).isEqualTo("BANK_DECLINE");
        assertThat(result.latestPaymentTimestamp()).isEqualTo(newFailure.getCreatedAt());
    }
}
