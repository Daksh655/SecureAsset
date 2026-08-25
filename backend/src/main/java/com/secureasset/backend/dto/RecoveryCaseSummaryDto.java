package com.secureasset.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecoveryCaseSummaryDto(
        UUID id,
        UUID customerId,
        UUID orderId,
        UUID paymentId,
        String problemType,
        BigDecimal riskAmount,
        Integer recoveryScore,
        String priority,
        String status,
        String agentStatus,
        OffsetDateTime detectedAt
) {}
