package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.OrderDetailsResult;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.repository.OrderRepository;
import com.secureasset.backend.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GetOrderDetailsTool implements AgentTool<GetOrderDetailsTool.Input, OrderDetailsResult> {

    public record Input(UUID orderId) {}

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public GetOrderDetailsTool(OrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public String getName() {
        return "getOrderDetails";
    }

    @Override
    public String getDescription() {
        return "Retrieve bounded details about a specific order, including aggregated payment attempts, without exposing raw payment lists.";
    }

    @Override
    public Class<Input> getInputSchema() {
        return Input.class;
    }

    @Override
    public OrderDetailsResult execute(Input input) {
        if (input == null || input.orderId() == null) {
            throw new IllegalArgumentException("Input and orderId must not be null");
        }

        UUID orderId = input.orderId();

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order not found with id: " + orderId);
        }

        Order order = orderOpt.get();

        // Since an order only has a few payments typically, we fetch the bounded list
        List<Payment> payments = paymentRepository.findByOrderId(orderId);

        long paymentAttempts = payments.size();
        long successfulPaymentCount = payments.stream()
                .filter(p -> Payment.PaymentStatus.CAPTURED.equals(p.getStatus()))
                .count();
        long failedPaymentCount = payments.stream()
                .filter(p -> Payment.PaymentStatus.FAILED.equals(p.getStatus()))
                .count();

        Payment latestPayment = payments.stream()
                .max(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        String latestPaymentStatus = null;
        String latestFailureReason = null;
        OffsetDateTime latestPaymentTimestamp = null;

        if (latestPayment != null) {
            latestPaymentStatus = latestPayment.getStatus() != null ? latestPayment.getStatus().name() : null;
            latestFailureReason = latestPayment.getFailureReason() != null ? latestPayment.getFailureReason().name() : null;
            latestPaymentTimestamp = latestPayment.getCreatedAt();
        }

        return new OrderDetailsResult(
                orderId,
                order.getAmount(),
                order.getCurrency(),
                order.getStatus() != null ? order.getStatus().name() : null,
                order.getCreatedAt(),
                paymentAttempts,
                successfulPaymentCount,
                failedPaymentCount,
                latestPaymentStatus,
                latestFailureReason,
                latestPaymentTimestamp
        );
    }
}
