package com.secureasset.backend.service;

import com.secureasset.backend.dto.GlobalAuditLogDto;
import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.DemoDatasetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final DemoDatasetRepository demoDatasetRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository, DemoDatasetRepository demoDatasetRepository) {
        this.auditLogRepository = auditLogRepository;
        this.demoDatasetRepository = demoDatasetRepository;
    }

    public PageResponse<GlobalAuditLogDto> searchAuditLogs(
            String eventType,
            UUID caseId,
            int page,
            int size
    ) {
        if (!demoDatasetRepository.existsById(DatasetService.DEMO_DATASET_ID)) {
            return new PageResponse<>(List.of(), 0, size, 0, 0);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        AuditLog.EventType eventTypeEnum = eventType != null && !eventType.trim().isEmpty() ? AuditLog.EventType.valueOf(eventType) : null;

        Page<AuditLog> logPage = auditLogRepository.searchAuditLogsScoped(
                DatasetService.DEMO_DATASET_ID, eventTypeEnum, caseId, pageable);

        List<GlobalAuditLogDto> content = logPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return new PageResponse<>(
                content,
                logPage.getNumber(),
                logPage.getSize(),
                logPage.getTotalElements(),
                logPage.getTotalPages()
        );
    }

    private GlobalAuditLogDto mapToDto(AuditLog log) {
        return new GlobalAuditLogDto(
                log.getId(),
                log.getEventType().name(),
                log.getActorType().name(),
                log.getToolName(),
                log.getMessage(),
                log.isSuccess(),
                log.getCreatedAt(),
                log.getRecoveryCase() != null ? log.getRecoveryCase().getId() : null,
                log.getRecoveryAction() != null ? log.getRecoveryAction().getId() : null
        );
    }
}
