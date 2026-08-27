package com.secureasset.backend.controller;

import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.dto.RecoveryActionSummaryDto;
import com.secureasset.backend.service.RecoveryActionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recovery-actions")
public class RecoveryActionController {

    private final RecoveryActionQueryService recoveryActionQueryService;

    public RecoveryActionController(RecoveryActionQueryService recoveryActionQueryService) {
        this.recoveryActionQueryService = recoveryActionQueryService;
    }

    @GetMapping
    public PageResponse<RecoveryActionSummaryDto> getRecoveryActions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return recoveryActionQueryService.searchActions(status, approvalStatus, actionType, page, size);
    }
}
