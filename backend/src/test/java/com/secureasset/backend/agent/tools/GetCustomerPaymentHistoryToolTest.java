package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.CustomerPaymentHistoryResult;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetCustomerPaymentHistoryToolTest {

    private PaymentRepository paymentRepository;
    private GetCustomerPaymentHistoryTool tool;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        tool = new GetCustomerPaymentHistoryTool(paymentRepository);
    }

    @Test
    void emptyPaymentHistoryReturnsEmptyResult() {
        UUID customerId = UUID.randomUUID();
        when(paymentRepository.countByCustomerId(customerId)).thenReturn(0L);

        CustomerPaymentHistoryResult result = tool.getCustomerPaymentHistory(customerId);

        assertThat(result.totalPayments()).isEqualTo(0);
        assertThat(result.successfulPayments()).isEqualTo(0);
        assertThat(result.totalCapturedAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.recentFailureReasons()).isEmpty();
    }

    @Test
    void customerWithSuccessfulAndFailedPaymentsHasCorrectAggregations() {
        UUID customerId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        when(paymentRepository.countByCustomerId(customerId)).thenReturn(25L);
        when(paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED)).thenReturn(20L);
        when(paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.FAILED)).thenReturn(5L);
        when(paymentRepository.sumAmountByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED))
                .thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.findLastSuccessfulPaymentDate(customerId)).thenReturn(now.minusDays(1));
        when(paymentRepository.findLastFailedPaymentDate(customerId)).thenReturn(now);

        Payment failed1 = new Payment();
        failed1.setStatus(Payment.PaymentStatus.FAILED);
        failed1.setFailureReason(Payment.FailureReason.TIMEOUT);

        Payment failed2 = new Payment();
        failed2.setStatus(Payment.PaymentStatus.FAILED);
        failed2.setFailureReason(Payment.FailureReason.INSUFFICIENT_FUNDS);

        Payment success = new Payment();
        success.setStatus(Payment.PaymentStatus.CAPTURED);

        when(paymentRepository.findRecentPaymentsByCustomerId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failed1, success, failed2)));

        CustomerPaymentHistoryResult result = tool.getCustomerPaymentHistory(customerId);

        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.totalPayments()).isEqualTo(25);
        assertThat(result.successfulPayments()).isEqualTo(20);
        assertThat(result.failedPayments()).isEqualTo(5);
        assertThat(result.totalCapturedAmount()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(result.averageCapturedAmount()).isEqualTo(new BigDecimal("50.00")); // 1000 / 20
        assertThat(result.lastSuccessfulPaymentAt()).isEqualTo(now.minusDays(1));
        assertThat(result.lastFailedPaymentAt()).isEqualTo(now);

        assertThat(result.recentFailedPaymentCount()).isEqualTo(2);
        assertThat(result.recentSuccessfulPaymentCount()).isEqualTo(1);
        assertThat(result.recentFailureReasons()).containsExactlyInAnyOrder("TIMEOUT", "INSUFFICIENT_FUNDS");

        // Verify bounds constraint explicitly (page size 10)
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findRecentPaymentsByCustomerId(eq(customerId), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(10);
    }
}
