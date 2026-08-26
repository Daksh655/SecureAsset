package com.secureasset.backend.agent.tools.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerRecoveryProfileResult(
        UUID customerId,
        long totalOrders,
        long successfulOrders,
        long failedOrders,
        long totalPayments,
        long successfulPayments,
        long failedPayments,
        BigDecimal successfulPaymentRate,
        BigDecimal totalCapturedAmount,
        BigDecimal averageSuccessfulPaymentAmount,
        long previousRecoveryAttempts,
        long previousSuccessfulRecoveries,
        long previousFailedRecoveries,
        BigDecimal recoverySuccessRate,
        OffsetDateTime lastSuccessfulPaymentAt,
        Long daysSinceLastSuccessfulPayment,
        String preferredPaymentMethod
) {}
