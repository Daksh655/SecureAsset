package com.secureasset.backend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecoveryActionDto(
        UUID id,
        String actionType,
        BigDecimal amount,
        String status,
        String approvalStatus,
        String razorpayReference,
        String result,
        OffsetDateTime requestedAt,
        OffsetDateTime executedAt
) {}
