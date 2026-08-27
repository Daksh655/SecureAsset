package com.secureasset.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GlobalAuditLogDto(
        UUID id,
        String eventType,
        String actorType,
        String toolName,
        String message,
        boolean success,
        OffsetDateTime createdAt,
        UUID recoveryCaseId,
        UUID recoveryActionId
) {}
