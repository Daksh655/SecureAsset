package com.secureasset.backend.agent.tools.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CustomerPaymentHistoryResult(
        UUID customerId,
        long totalPayments,
        long successfulPayments,
        long failedPayments,
        BigDecimal totalCapturedAmount,
        BigDecimal averageCapturedAmount,
        OffsetDateTime lastSuccessfulPaymentAt,
        OffsetDateTime lastFailedPaymentAt,
        List<String> recentFailureReasons,
        long recentFailedPaymentCount,
        long recentSuccessfulPaymentCount
) {}
