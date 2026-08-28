package com.secureasset.backend.service;

import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.dto.RecoveryActionSummaryDto;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.DemoDatasetRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecoveryActionQueryService {

    private final RecoveryActionRepository recoveryActionRepository;
    private final DemoDatasetRepository demoDatasetRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;

    public RecoveryActionQueryService(RecoveryActionRepository recoveryActionRepository, DemoDatasetRepository demoDatasetRepository, RecoveryCaseRepository recoveryCaseRepository) {
        this.recoveryActionRepository = recoveryActionRepository;
        this.demoDatasetRepository = demoDatasetRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
    }

    public PageResponse<RecoveryActionSummaryDto> searchActions(
            String status,
            String approvalStatus,
            String actionType,
            int page,
            int size
    ) {
        if (!demoDatasetRepository.existsById(DatasetService.DEMO_DATASET_ID)) {
            return new PageResponse<>(List.of(), 0, size, 0, 0);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));

        RecoveryAction.Status statusEnum = status != null && !status.trim().isEmpty() ? RecoveryAction.Status.valueOf(status) : null;
        RecoveryAction.ApprovalStatus approvalStatusEnum = approvalStatus != null && !approvalStatus.trim().isEmpty() ? RecoveryAction.ApprovalStatus.valueOf(approvalStatus) : null;
        RecoveryAction.ActionType actionTypeEnum = actionType != null && !actionType.trim().isEmpty() ? RecoveryAction.ActionType.valueOf(actionType) : null;

        Page<RecoveryAction> actionPage = recoveryActionRepository.searchActionsScoped(
                DatasetService.DEMO_DATASET_ID, statusEnum, approvalStatusEnum, actionTypeEnum, pageable);

        List<RecoveryActionSummaryDto> content = new ArrayList<>(actionPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList()));

        if (statusEnum == null && approvalStatusEnum == RecoveryAction.ApprovalStatus.PENDING) {
            List<com.secureasset.backend.entity.RecoveryCase> escalatedCases =
                recoveryCaseRepository.findEscalatedCasesWithNoAction(DatasetService.DEMO_DATASET_ID);
            
            for (com.secureasset.backend.entity.RecoveryCase rc : escalatedCases) {
                content.add(new RecoveryActionSummaryDto(
                    null,
                    rc.getId(),
                    "ESCALATE_TO_MERCHANT",
                    rc.getRiskAmount(),
                    "PENDING",
                    "PENDING",
                    null,
                    rc.getAnalyzedAt() != null ? rc.getAnalyzedAt() : rc.getDetectedAt(),
                    null, null, null, null, null
                ));
            }
        }

        return new PageResponse<>(
                content,
                actionPage.getNumber(),
                actionPage.getSize(),
                actionPage.getTotalElements(),
                actionPage.getTotalPages()
        );
    }

    private RecoveryActionSummaryDto mapToDto(RecoveryAction action) {
        return new RecoveryActionSummaryDto(
                action.getId(),
                action.getRecoveryCase() != null ? action.getRecoveryCase().getId() : null,
                action.getActionType().name(),
                action.getAmount(),
                action.getStatus().name(),
                action.getApprovalStatus().name(),
                action.getRazorpayReference(),
                action.getRequestedAt(),
                action.getApprovedAt(),
                action.getExecutedAt(),
                action.getCompletedAt(),
                action.getErrorCode(),
                action.getErrorMessage()
        );
    }
}
