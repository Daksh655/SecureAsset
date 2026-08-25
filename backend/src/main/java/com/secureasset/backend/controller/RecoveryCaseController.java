package com.secureasset.backend.controller;

import com.secureasset.backend.dto.AuditLogDto;
import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.dto.RecoveryActionDto;
import com.secureasset.backend.dto.RecoveryCaseDetailDto;
import com.secureasset.backend.dto.RecoveryCaseSummaryDto;
import com.secureasset.backend.service.RecoveryCaseService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recovery-cases")
public class RecoveryCaseController {

    private final RecoveryCaseService recoveryCaseService;

    public RecoveryCaseController(RecoveryCaseService recoveryCaseService) {
        this.recoveryCaseService = recoveryCaseService;
    }

    @GetMapping
    public PageResponse<RecoveryCaseSummaryDto> getRecoveryCases(
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return recoveryCaseService.searchCases(priority, status, problemType, minAmount, maxAmount, minScore, page, size);
    }

    @GetMapping("/{id}")
    public RecoveryCaseDetailDto getCaseDetails(@PathVariable UUID id) {
        return recoveryCaseService.getCaseDetails(id);
    }

    @GetMapping("/{id}/actions")
    public List<RecoveryActionDto> getCaseActions(@PathVariable UUID id) {
        return recoveryCaseService.getCaseActions(id);
    }

    @GetMapping("/{id}/audit")
    public List<AuditLogDto> getCaseAuditLogs(@PathVariable UUID id) {
        return recoveryCaseService.getCaseAuditLogs(id);
    }
}
