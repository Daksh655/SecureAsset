package com.secureasset.backend.agent.tools;

import com.secureasset.backend.agent.tools.dto.RelatedRecoveryCasesResult;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class GetRelatedRecoveryCasesTool implements AgentTool<GetRelatedRecoveryCasesTool.Input, RelatedRecoveryCasesResult> {

    public record Input(UUID customerId, UUID orderId) {}

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;

    public GetRelatedRecoveryCasesTool(RecoveryCaseRepository recoveryCaseRepository, RecoveryActionRepository recoveryActionRepository) {
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.recoveryActionRepository = recoveryActionRepository;
    }

    @Override
    public String getName() {
        return "getRelatedRecoveryCases";
    }

    @Override
    public String getDescription() {
        return "Retrieve a bounded summary of previous recovery cases and actions for a customer, optionally filtered by orderId, to determine if recovery was already attempted.";
    }

    @Override
    public Class<Input> getInputSchema() {
        return Input.class;
    }

    @Override
    public RelatedRecoveryCasesResult execute(Input input) {
        if (input == null || input.customerId() == null) {
            throw new IllegalArgumentException("Input and customerId must not be null");
        }

        UUID customerId = input.customerId();
        UUID orderId = input.orderId();

        List<RecoveryCase> cases = recoveryCaseRepository.findByCustomerId(customerId);

        if (orderId != null) {
            cases = cases.stream()
                    .filter(c -> c.getOrder() != null && orderId.equals(c.getOrder().getId()))
                    .collect(Collectors.toList());
        }

        long totalRelatedCases = cases.size();
        
        long activeCaseCount = cases.stream()
                .filter(c -> c.getStatus() != null && isActive(c.getStatus()))
                .count();
                
        long recoveredCaseCount = cases.stream()
                .filter(c -> RecoveryCase.Status.RECOVERED.equals(c.getStatus()))
                .count();
                
        long failedCaseCount = cases.stream()
                .filter(c -> RecoveryCase.Status.FAILED.equals(c.getStatus()))
                .count();
                
        long dismissedCaseCount = cases.stream()
                .filter(c -> RecoveryCase.Status.DISMISSED.equals(c.getStatus()))
                .count();

        // Recent cases up to 5
        List<RecoveryCase> recentCasesList = cases.stream()
                .sorted(Comparator.comparing(RecoveryCase::getDetectedAt, Comparator.nullsFirst(Comparator.reverseOrder())))
                .limit(5)
                .collect(Collectors.toList());

        List<RelatedRecoveryCasesResult.CaseSummary> recentCases = recentCasesList.stream()
                .map(c -> new RelatedRecoveryCasesResult.CaseSummary(
                        c.getId(),
                        c.getStatus() != null ? c.getStatus().name() : null,
                        c.getProblemType() != null ? c.getProblemType().name() : null,
                        c.getDetectedAt()
                ))
                .collect(Collectors.toList());

        // For recent actions, we find actions for the recent cases
        List<RecoveryAction> recentActionsList = recentCasesList.stream()
                .flatMap(c -> recoveryActionRepository.findByRecoveryCaseIdOrderByRequestedAtDesc(c.getId()).stream())
                .sorted(Comparator.comparing(RecoveryAction::getRequestedAt, Comparator.nullsFirst(Comparator.reverseOrder())))
                .limit(5)
                .collect(Collectors.toList());

        List<RelatedRecoveryCasesResult.ActionSummary> recentActions = recentActionsList.stream()
                .map(a -> new RelatedRecoveryCasesResult.ActionSummary(
                        a.getId(),
                        a.getActionType() != null ? a.getActionType().name() : null,
                        a.getStatus() != null ? a.getStatus().name() : null,
                        a.getRequestedAt(),
                        a.getCompletedAt()
                ))
                .collect(Collectors.toList());

        OffsetDateTime lastRecoveryAttemptAt = recentActionsList.stream()
                .map(RecoveryAction::getRequestedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        String lastRecoveryOutcome = recentActionsList.stream()
                .max(Comparator.comparing(RecoveryAction::getRequestedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(a -> a.getStatus() != null ? a.getStatus().name() : null)
                .orElse(null);

        return new RelatedRecoveryCasesResult(
                customerId,
                orderId,
                totalRelatedCases,
                activeCaseCount,
                recoveredCaseCount,
                failedCaseCount,
                dismissedCaseCount,
                recentCases,
                recentActions,
                lastRecoveryAttemptAt,
                lastRecoveryOutcome
        );
    }
    
    private boolean isActive(RecoveryCase.Status status) {
        return status == RecoveryCase.Status.NEW ||
               status == RecoveryCase.Status.ANALYZING ||
               status == RecoveryCase.Status.ACTION_REQUIRED ||
               status == RecoveryCase.Status.PENDING_APPROVAL ||
               status == RecoveryCase.Status.EXECUTING;
    }
}
