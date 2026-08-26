package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.CustomerRecoveryProfileResult;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.CustomerRepository;
import com.secureasset.backend.repository.OrderRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetCustomerRecoveryProfileToolTest {

    private CustomerRepository customerRepository;
    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private RecoveryCaseRepository recoveryCaseRepository;
    private GetCustomerRecoveryProfileTool tool;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        recoveryCaseRepository = mock(RecoveryCaseRepository.class);
        
        tool = new GetCustomerRecoveryProfileTool(
                customerRepository, orderRepository, paymentRepository, recoveryCaseRepository);
    }

    @Test
    void customerNotFoundThrowsException() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsById(customerId)).thenReturn(false);

        assertThatThrownBy(() -> tool.execute(new GetCustomerRecoveryProfileTool.Input(customerId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer not found");
    }

    @Test
    void zeroHistoryCustomerIsHandledSafely() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsById(customerId)).thenReturn(true);
        when(orderRepository.findByCustomerId(customerId)).thenReturn(Collections.emptyList());
        when(paymentRepository.countByCustomerId(customerId)).thenReturn(0L);
        when(paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED)).thenReturn(0L);
        when(paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.FAILED)).thenReturn(0L);
        when(paymentRepository.sumAmountByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED)).thenReturn(null);
        when(recoveryCaseRepository.findByCustomerId(customerId)).thenReturn(Collections.emptyList());
        when(paymentRepository.findLastSuccessfulPaymentDate(customerId)).thenReturn(null);
        when(paymentRepository.findRecentPaymentsByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        CustomerRecoveryProfileResult result = tool.execute(new GetCustomerRecoveryProfileTool.Input(customerId));

        assertThat(result.totalOrders()).isEqualTo(0);
        assertThat(result.successfulPaymentRate()).isEqualByComparingTo("0");
        assertThat(result.averageSuccessfulPaymentAmount()).isEqualByComparingTo("0");
        assertThat(result.recoverySuccessRate()).isEqualByComparingTo("0");
        assertThat(result.preferredPaymentMethod()).isEqualTo("UNKNOWN");
        assertThat(result.lastSuccessfulPaymentAt()).isNull();
        assertThat(result.daysSinceLastSuccessfulPayment()).isNull();
    }

    @Test
    void highValueReturningCustomerProfileIsCalculatedCorrectly() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.existsById(customerId)).thenReturn(true);

        Order o1 = new Order(); o1.setStatus(Order.OrderStatus.PAID);
        Order o2 = new Order(); o2.setStatus(Order.OrderStatus.PAID);
        Order o3 = new Order(); o3.setStatus(Order.OrderStatus.FAILED);
        when(orderRepository.findByCustomerId(customerId)).thenReturn(List.of(o1, o2, o3));

        when(paymentRepository.countByCustomerId(customerId)).thenReturn(10L);
        when(paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED)).thenReturn(8L);
        when(paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.FAILED)).thenReturn(2L);
        when(paymentRepository.sumAmountByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED)).thenReturn(new BigDecimal("16000.00"));

        OffsetDateTime lastPaymentDate = OffsetDateTime.now().minusDays(5);
        when(paymentRepository.findLastSuccessfulPaymentDate(customerId)).thenReturn(lastPaymentDate);

        Payment p1 = new Payment(); p1.setMethod("upi");
        Payment p2 = new Payment(); p2.setMethod("upi");
        Payment p3 = new Payment(); p3.setMethod("card");
        when(paymentRepository.findRecentPaymentsByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2, p3)));

        RecoveryCase rc1 = new RecoveryCase(); rc1.setStatus(RecoveryCase.Status.RECOVERED);
        RecoveryCase rc2 = new RecoveryCase(); rc2.setStatus(RecoveryCase.Status.RECOVERED);
        RecoveryCase rc3 = new RecoveryCase(); rc3.setStatus(RecoveryCase.Status.FAILED);
        RecoveryCase rc4 = new RecoveryCase(); rc4.setStatus(RecoveryCase.Status.ANALYZING);
        when(recoveryCaseRepository.findByCustomerId(customerId)).thenReturn(List.of(rc1, rc2, rc3, rc4));

        CustomerRecoveryProfileResult result = tool.execute(new GetCustomerRecoveryProfileTool.Input(customerId));

        assertThat(result.totalOrders()).isEqualTo(3);
        assertThat(result.successfulOrders()).isEqualTo(2);
        assertThat(result.failedOrders()).isEqualTo(1);
        
        assertThat(result.totalPayments()).isEqualTo(10);
        assertThat(result.successfulPayments()).isEqualTo(8);
        assertThat(result.successfulPaymentRate()).isEqualByComparingTo("80.00");
        
        assertThat(result.totalCapturedAmount()).isEqualByComparingTo("16000.00");
        assertThat(result.averageSuccessfulPaymentAmount()).isEqualByComparingTo("2000.00");
        
        assertThat(result.previousRecoveryAttempts()).isEqualTo(4);
        assertThat(result.previousSuccessfulRecoveries()).isEqualTo(2);
        assertThat(result.previousFailedRecoveries()).isEqualTo(1);
        // (2 successful / 3 completed) * 100 = 66.67
        assertThat(result.recoverySuccessRate()).isEqualByComparingTo("66.67");
        
        assertThat(result.lastSuccessfulPaymentAt()).isEqualTo(lastPaymentDate);
        assertThat(result.daysSinceLastSuccessfulPayment()).isEqualTo(5);
        
        assertThat(result.preferredPaymentMethod()).isEqualTo("upi");
    }
}
