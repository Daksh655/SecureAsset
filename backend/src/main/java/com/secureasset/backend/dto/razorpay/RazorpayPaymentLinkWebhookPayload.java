package com.secureasset.backend.dto.razorpay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayPaymentLinkWebhookPayload(
        String event,
        List<String> contains,
        Payload payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            @JsonProperty("payment_link") PaymentLinkContainer paymentLink,
            PaymentContainer payment
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentLinkContainer(
            PaymentLinkEntity entity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentContainer(
            PaymentEntity entity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentLinkEntity(
            String id,
            @JsonProperty("reference_id") String referenceId,
            String status,
            Long amount,
            @JsonProperty("amount_paid") Long amountPaid,
            String currency
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentEntity(
            String id,
            @JsonProperty("order_id") String orderId,
            String status
    ) {}
}
