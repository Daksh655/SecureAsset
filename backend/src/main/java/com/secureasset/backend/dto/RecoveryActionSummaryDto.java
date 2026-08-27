package com.secureasset.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecoveryActionSummaryDto(
        UUID id,
        UUID recoveryCaseId,
        String actionType,
        BigDecimal amount,
        String status,
        String approvalStatus,
        String razorpayReference,
        OffsetDateTime requestedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime executedAt,
        OffsetDateTime completedAt,
        String errorCode,
        String errorMessage
) {}
