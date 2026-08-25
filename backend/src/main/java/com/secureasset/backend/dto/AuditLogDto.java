package com.secureasset.backend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        String eventType,
        String actorType,
        String toolName,
        String message,
        boolean success,
        OffsetDateTime createdAt
) {}
