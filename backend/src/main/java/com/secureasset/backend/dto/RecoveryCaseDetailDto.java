package com.secureasset.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecoveryCaseDetailDto(
        UUID id,
        CustomerSummaryDto customer,
        OrderSummaryDto order,
        PaymentSummaryDto payment,
        String problemType,
        BigDecimal riskAmount,
        Integer recoveryScore,
        String priority,
        String status,
        String agentStatus,
        String agentRecommendation,
        BigDecimal agentConfidence,
        String agentReason,
        OffsetDateTime detectedAt,
        OffsetDateTime analyzedAt
) {
    public record CustomerSummaryDto(UUID id, String name, String email) {}
    public record OrderSummaryDto(UUID id, BigDecimal amount, String currency, String status) {}
    public record PaymentSummaryDto(UUID id, BigDecimal amount, String status, String failureReason, Integer attemptNumber) {}
}
