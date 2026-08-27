package com.secureasset.backend.controller;

import com.secureasset.backend.dto.GlobalAuditLogDto;
import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.service.AuditLogQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public PageResponse<GlobalAuditLogDto> getAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return auditLogQueryService.searchAuditLogs(eventType, caseId, page, size);
    }
}
