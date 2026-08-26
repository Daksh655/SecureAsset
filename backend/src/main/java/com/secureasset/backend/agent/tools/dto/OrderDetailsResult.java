package com.secureasset.backend.agent.tools.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderDetailsResult(
        UUID orderId,
        BigDecimal amount,
        String currency,
        String orderStatus,
        OffsetDateTime createdAt,
        long paymentAttempts,
        long successfulPaymentCount,
        long failedPaymentCount,
        String latestPaymentStatus,
        String latestFailureReason,
        OffsetDateTime latestPaymentTimestamp
) {}
