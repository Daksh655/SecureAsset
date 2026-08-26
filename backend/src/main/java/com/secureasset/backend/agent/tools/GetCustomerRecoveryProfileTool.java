package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.CustomerRecoveryProfileResult;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.CustomerRepository;
import com.secureasset.backend.repository.OrderRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GetCustomerRecoveryProfileTool implements AgentTool<GetCustomerRecoveryProfileTool.Input, CustomerRecoveryProfileResult> {

    public record Input(UUID customerId) {}

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;

    public GetCustomerRecoveryProfileTool(
            CustomerRepository customerRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            RecoveryCaseRepository recoveryCaseRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
    }

    @Override
    public String getName() {
        return "getCustomerRecoveryProfile";
    }

    @Override
    public String getDescription() {
        return "Retrieve a bounded, derived profile of the customer's payment and recovery behavior. Provides metrics without exposing raw payment lists.";
    }

    @Override
    public Class<Input> getInputSchema() {
        return Input.class;
    }

    @Override
    public CustomerRecoveryProfileResult execute(Input input) {
        if (input == null || input.customerId() == null) {
            throw new IllegalArgumentException("customerId must not be null");
        }

        UUID customerId = input.customerId();

        if (!customerRepository.existsById(customerId)) {
            throw new IllegalArgumentException("Customer not found with id: " + customerId);
        }

        List<Order> orders = orderRepository.findByCustomerId(customerId);
        long totalOrders = orders.size();
        long successfulOrders = orders.stream().filter(o -> Order.OrderStatus.PAID.equals(o.getStatus())).count();
        long failedOrders = orders.stream().filter(o -> Order.OrderStatus.FAILED.equals(o.getStatus())).count();

        long totalPayments = paymentRepository.countByCustomerId(customerId);
        long successfulPayments = paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED);
        long failedPayments = paymentRepository.countByCustomerIdAndStatus(customerId, Payment.PaymentStatus.FAILED);

        BigDecimal successfulPaymentRate = BigDecimal.ZERO;
        if (totalPayments > 0) {
            successfulPaymentRate = BigDecimal.valueOf(successfulPayments)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP);
        }

        BigDecimal totalCapturedAmount = paymentRepository.sumAmountByCustomerIdAndStatus(customerId, Payment.PaymentStatus.CAPTURED);
        if (totalCapturedAmount == null) {
            totalCapturedAmount = BigDecimal.ZERO;
        }

        BigDecimal averageSuccessfulPaymentAmount = BigDecimal.ZERO;
        if (successfulPayments > 0) {
            averageSuccessfulPaymentAmount = totalCapturedAmount.divide(BigDecimal.valueOf(successfulPayments), 2, RoundingMode.HALF_UP);
        }

        List<RecoveryCase> cases = recoveryCaseRepository.findByCustomerId(customerId);
        long previousRecoveryAttempts = cases.size();
        long previousSuccessfulRecoveries = cases.stream().filter(c -> RecoveryCase.Status.RECOVERED.equals(c.getStatus())).count();
        long previousFailedRecoveries = cases.stream().filter(c -> RecoveryCase.Status.FAILED.equals(c.getStatus())).count();

        BigDecimal recoverySuccessRate = BigDecimal.ZERO;
        long completedRecoveries = previousSuccessfulRecoveries + previousFailedRecoveries;
        if (completedRecoveries > 0) {
            recoverySuccessRate = BigDecimal.valueOf(previousSuccessfulRecoveries)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(completedRecoveries), 2, RoundingMode.HALF_UP);
        }

        OffsetDateTime lastSuccessfulPaymentAt = paymentRepository.findLastSuccessfulPaymentDate(customerId);
        Long daysSinceLastSuccessfulPayment = null;
        if (lastSuccessfulPaymentAt != null) {
            daysSinceLastSuccessfulPayment = ChronoUnit.DAYS.between(lastSuccessfulPaymentAt, OffsetDateTime.now());
        }

        String preferredPaymentMethod = "UNKNOWN";
        Page<Payment> recentPayments = paymentRepository.findRecentPaymentsByCustomerId(customerId, PageRequest.of(0, 20));
        if (recentPayments.hasContent()) {
            Map<String, Long> methodCounts = recentPayments.stream()
                    .filter(p -> p.getMethod() != null)
                    .map(Payment::getMethod)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                    
            preferredPaymentMethod = methodCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("UNKNOWN");
        }

        return new CustomerRecoveryProfileResult(
                customerId,
                totalOrders,
                successfulOrders,
                failedOrders,
                totalPayments,
                successfulPayments,
                failedPayments,
                successfulPaymentRate,
                totalCapturedAmount,
                averageSuccessfulPaymentAmount,
                previousRecoveryAttempts,
                previousSuccessfulRecoveries,
                previousFailedRecoveries,
                recoverySuccessRate,
                lastSuccessfulPaymentAt,
                daysSinceLastSuccessfulPayment,
                preferredPaymentMethod
        );
    }
}
