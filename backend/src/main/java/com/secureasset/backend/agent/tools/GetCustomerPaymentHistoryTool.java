package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.CustomerPaymentHistoryResult;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.repository.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class GetCustomerPaymentHistoryTool implements AgentTool<GetCustomerPaymentHistoryTool.Input, CustomerPaymentHistoryResult> {

    public record Input(UUID customerId) {}

    private final PaymentRepository paymentRepository;

    public GetCustomerPaymentHistoryTool(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public String getName() {
        return "getCustomerPaymentHistory";
    }

    @Override
    public String getDescription() {
        return "Retrieve a bounded summary of a customer's payment history to assist in recovery decisions.";
    }

    @Override
    public Class<Input> getInputSchema() {
        return Input.class;
    }

    @Override
    public CustomerPaymentHistoryResult execute(Input input) {
        if (input == null || input.customerId() == null) {
            throw new IllegalArgumentException("Input and customerId must not be null");
        }
        return getCustomerPaymentHistory(input.customerId());
    }

    public CustomerPaymentHistoryResult getCustomerPaymentHistory(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }

        long totalPayments = paymentRepository.countByCustomerId(customerId);
        if (totalPayments == 0) {
            return new CustomerPaymentHistoryResult(
                    customerId,
                    0, 0, 0,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null,
                    List.of(),
                    0, 0
            );
        }

        long successfulPayments = paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED);
        long failedPayments = paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.FAILED);

        BigDecimal totalCapturedAmount = paymentRepository.sumAmountByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED);
        if (totalCapturedAmount == null) {
            totalCapturedAmount = BigDecimal.ZERO;
        }

        BigDecimal averageCapturedAmount = BigDecimal.ZERO;
        if (successfulPayments > 0) {
            averageCapturedAmount = totalCapturedAmount.divide(BigDecimal.valueOf(successfulPayments), 2, RoundingMode.HALF_UP);
        }

        OffsetDateTime lastSuccessful = paymentRepository.findLastSuccessfulPaymentDate(customerId);
        OffsetDateTime lastFailed = paymentRepository.findLastFailedPaymentDate(customerId);

        // Fetch up to 10 most recent payments to inspect recent patterns
        Page<Payment> recentPaymentsPage = paymentRepository.findRecentPaymentsByCustomerId(customerId, PageRequest.of(0, 10));
        List<Payment> recentPayments = recentPaymentsPage.getContent();

        long recentFailedCount = recentPayments.stream()
                .filter(p -> Payment.PaymentStatus.FAILED.equals(p.getStatus()))
                .count();

        long recentSuccessfulCount = recentPayments.stream()
                .filter(p -> Payment.PaymentStatus.CAPTURED.equals(p.getStatus()))
                .count();

        List<String> recentFailureReasons = recentPayments.stream()
                .filter(p -> Payment.PaymentStatus.FAILED.equals(p.getStatus()) && p.getFailureReason() != null)
                .map(p -> p.getFailureReason().name())
                .distinct()
                .collect(Collectors.toList());

        return new CustomerPaymentHistoryResult(
                customerId,
                totalPayments,
                successfulPayments,
                failedPayments,
                totalCapturedAmount,
                averageCapturedAmount,
                lastSuccessful,
                lastFailed,
                recentFailureReasons,
                recentFailedCount,
                recentSuccessfulCount
        );
    }
}
