package com.secureasset.backend.agent.tools.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RelatedRecoveryCasesResult(
        UUID customerId,
        UUID orderId,
        long totalRelatedCases,
        long activeCaseCount,
        long recoveredCaseCount,
        long failedCaseCount,
        long dismissedCaseCount,
        List<CaseSummary> recentCases,
        List<ActionSummary> recentActions,
        OffsetDateTime lastRecoveryAttemptAt,
        String lastRecoveryOutcome
) {
    public record CaseSummary(
            UUID id,
            String status,
            String problemType,
            OffsetDateTime detectedAt
    ) {}

    public record ActionSummary(
            UUID id,
            String actionType,
            String status,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {}
}
