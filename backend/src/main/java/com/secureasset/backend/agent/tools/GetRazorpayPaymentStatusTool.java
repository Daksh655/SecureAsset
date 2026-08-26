package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.RazorpayPaymentStatusResult;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.integration.RazorpayPaymentService;
import com.secureasset.backend.repository.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GetRazorpayPaymentStatusTool implements AgentTool<GetRazorpayPaymentStatusTool.Input, RazorpayPaymentStatusResult> {

    public record Input(String razorpayPaymentId, UUID orderId) {}

    private final RazorpayPaymentService razorpayPaymentService;
    private final PaymentRepository paymentRepository;

    public GetRazorpayPaymentStatusTool(RazorpayPaymentService razorpayPaymentService, PaymentRepository paymentRepository) {
        this.razorpayPaymentService = razorpayPaymentService;
        this.paymentRepository = paymentRepository;
    }

    @Override
    public String getName() {
        return "getRazorpayPaymentStatus";
    }

    @Override
    public String getDescription() {
        return "Fetch the real-time status of a payment directly from Razorpay. Provide razorpayPaymentId if available, or orderId.";
    }

    @Override
    public Class<Input> getInputSchema() {
        return Input.class;
    }

    @Override
    public RazorpayPaymentStatusResult execute(Input input) {
        if (input == null || (input.razorpayPaymentId() == null && input.orderId() == null)) {
            throw new IllegalArgumentException("Must provide either razorpayPaymentId or orderId");
        }

        String paymentIdToFetch = input.razorpayPaymentId();
        UUID internalOrderId = input.orderId();

        if (paymentIdToFetch == null || paymentIdToFetch.isBlank()) {
            List<Payment> payments = paymentRepository.findByOrderId(internalOrderId);
            if (payments.isEmpty()) {
                throw new IllegalArgumentException("No payments found for orderId: " + internalOrderId);
            }
            
            // Get the most recent payment attempt
            Payment mostRecent = payments.stream()
                    .max(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow();
                    
            paymentIdToFetch = mostRecent.getRazorpayPaymentId();
            if (paymentIdToFetch == null) {
                throw new IllegalStateException("Internal payment has no razorpayPaymentId to fetch");
            }
        }

        Optional<RazorpayPaymentStatusResult> result = razorpayPaymentService.fetchPaymentDetails(paymentIdToFetch, internalOrderId);
        
        if (result.isEmpty()) {
            throw new IllegalStateException("Failed to fetch payment details from Razorpay or payment not found.");
        }
        
        return result.get();
    }
}
