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
    private final com.secureasset.backend.service.RecoveryActionExecutionService recoveryActionExecutionService;
    private final com.secureasset.backend.service.RevenueRiskEvaluationService revenueRiskEvaluationService;

    public RecoveryCaseController(RecoveryCaseService recoveryCaseService, com.secureasset.backend.service.RecoveryActionExecutionService recoveryActionExecutionService, com.secureasset.backend.service.RevenueRiskEvaluationService revenueRiskEvaluationService) {
        this.recoveryCaseService = recoveryCaseService;
        this.recoveryActionExecutionService = recoveryActionExecutionService;
        this.revenueRiskEvaluationService = revenueRiskEvaluationService;
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

    @PostMapping("/{id}/investigate")
    public RecoveryCaseDetailDto investigateCase(@PathVariable UUID id) {
        return recoveryCaseService.investigateCase(id);
    }

    @GetMapping("/{id}/actions")
    public List<RecoveryActionDto> getCaseActions(@PathVariable UUID id) {
        return recoveryCaseService.getCaseActions(id);
    }

    @GetMapping("/{id}/audit")
    public List<AuditLogDto> getCaseAuditLogs(@PathVariable UUID id) {
        return recoveryCaseService.getCaseAuditLogs(id);
    }

    public record ProposeActionRequestDto(
            String actionType,
            BigDecimal amount
    ) {}

    @PostMapping("/{id}/actions")
    public RecoveryActionDto proposeAction(@PathVariable UUID id, @RequestBody ProposeActionRequestDto request) {
        com.secureasset.backend.entity.RecoveryAction action = recoveryActionExecutionService.proposeAction(
            id, 
            com.secureasset.backend.entity.RecoveryAction.ActionType.valueOf(request.actionType()), 
            request.amount()
        );
        
        if (action.getStatus() == com.secureasset.backend.entity.RecoveryAction.Status.APPROVED) {
            recoveryActionExecutionService.executeAction(action.getId());
            // Re-fetch action to get the updated status/result after execution
        }
        return new RecoveryActionDto(
            action.getId(),
            action.getActionType().name(),
            action.getAmount(),
            action.getStatus().name(),
            action.getApprovalStatus().name(),
            action.getRazorpayReference(),
            action.getResult(),
            action.getRequestedAt(),
            action.getExecutedAt()
        );
    }

    public record ApproveRequestDto(
            String actionType,
            BigDecimal amount
    ) {}

    public record RejectRequestDto(
            String reason
    ) {}

    public record ApiResponse(
            boolean success,
            String message
    ) {}

    @PostMapping("/{id}/approve")
    public org.springframework.http.ResponseEntity<ApiResponse> approveAction(
            @PathVariable UUID id,
            @RequestBody ApproveRequestDto request) {
        try {
            if (request.actionType() == null || request.amount() == null) {
                return org.springframework.http.ResponseEntity.badRequest().body(new ApiResponse(false, "actionType and amount are required"));
            }
            
            com.secureasset.backend.entity.RecoveryAction.ActionType type = 
                    com.secureasset.backend.entity.RecoveryAction.ActionType.valueOf(request.actionType());

            UUID actionId = recoveryActionExecutionService.approveActionByCase(id, type, request.amount());
            
            // Execute the action now that it is approved
            recoveryActionExecutionService.executeAction(actionId);
            
            return org.springframework.http.ResponseEntity.ok(new ApiResponse(true, "Action approved and execution started successfully"));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (IllegalStateException e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(false, "Internal server error"));
        }
    }

    @PostMapping("/{id}/reject")
    public org.springframework.http.ResponseEntity<ApiResponse> rejectAction(
            @PathVariable UUID id,
            @RequestBody RejectRequestDto request) {
        try {
            String reason = request.reason();
            if (reason == null || reason.trim().isEmpty()) {
                reason = "Merchant rejected the recommendation without providing a reason.";
            }

            recoveryActionExecutionService.rejectActionByCase(id, reason);
            return org.springframework.http.ResponseEntity.ok(new ApiResponse(true, "Action rejected successfully"));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (IllegalStateException e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse(false, "Internal server error"));
        }
    }

    @PostMapping("/generate")
    public org.springframework.http.ResponseEntity<?> generateRecoveryCases() {
        try {
            int count = revenueRiskEvaluationService.evaluateDemoFailedPayments(
                    com.secureasset.backend.service.DatasetService.DEMO_DATASET_ID);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("message", "Generated " + count + " cases"));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
