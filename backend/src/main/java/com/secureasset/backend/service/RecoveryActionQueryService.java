package com.secureasset.backend.service;

import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.dto.RecoveryActionSummaryDto;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.DemoDatasetRepository;
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

    public RecoveryActionQueryService(RecoveryActionRepository recoveryActionRepository, DemoDatasetRepository demoDatasetRepository) {
        this.recoveryActionRepository = recoveryActionRepository;
        this.demoDatasetRepository = demoDatasetRepository;
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

        List<RecoveryActionSummaryDto> content = actionPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

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
