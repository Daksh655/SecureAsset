package com.secureasset.backend.agent.tools.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RazorpayPaymentStatusResult(
        String paymentId,
        UUID orderId,
        String status,
        BigDecimal amount,
        String currency,
        String method,
        boolean captured,
        OffsetDateTime createdAt,
        String failureReason,
        String failureCode,
        OffsetDateTime fetchedAt
) {}
