package com.secureasset.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureasset.backend.service.RecoveryActionExecutionService;
import com.secureasset.backend.service.RecoveryCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.secureasset.backend.service.RevenueRiskEvaluationService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

class RecoveryCaseControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    @Mock
    private RecoveryCaseService recoveryCaseService;

    @Mock
    private RecoveryActionExecutionService executionService;

    @Mock
    private RevenueRiskEvaluationService revenueRiskEvaluationService;

    private RecoveryCaseController recoveryCaseController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        recoveryCaseController = new RecoveryCaseController(recoveryCaseService, executionService, revenueRiskEvaluationService);
        mockMvc = MockMvcBuilders.standaloneSetup(recoveryCaseController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void validApproval() throws Exception {
        UUID caseId = UUID.randomUUID();
        RecoveryCaseController.ApproveRequestDto req = new RecoveryCaseController.ApproveRequestDto("CREATE_PAYMENT_LINK", new BigDecimal("7500.00"));

        mockMvc.perform(post("/api/recovery-cases/{id}/approve", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void invalidAction() throws Exception {
        UUID caseId = UUID.randomUUID();
        RecoveryCaseController.ApproveRequestDto req = new RecoveryCaseController.ApproveRequestDto("INVALID_ACTION", new BigDecimal("7500.00"));

        mockMvc.perform(post("/api/recovery-cases/{id}/approve", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void amountMismatch() throws Exception {
        UUID caseId = UUID.randomUUID();
        RecoveryCaseController.ApproveRequestDto req = new RecoveryCaseController.ApproveRequestDto("CREATE_PAYMENT_LINK", new BigDecimal("7500.00"));

        doThrow(new IllegalArgumentException("Action details do not match")).when(executionService)
                .approveActionByCase(eq(caseId), any(), any());

        mockMvc.perform(post("/api/recovery-cases/{id}/approve", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void policyBlockedAmount() throws Exception {
        UUID caseId = UUID.randomUUID();
        RecoveryCaseController.ApproveRequestDto req = new RecoveryCaseController.ApproveRequestDto("CREATE_PAYMENT_LINK", new BigDecimal("15000.00"));

        doThrow(new IllegalStateException("Amount exceeds limit")).when(executionService)
                .approveActionByCase(eq(caseId), any(), any());

        mockMvc.perform(post("/api/recovery-cases/{id}/approve", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void validRejection() throws Exception {
        UUID caseId = UUID.randomUUID();
        RecoveryCaseController.RejectRequestDto req = new RecoveryCaseController.RejectRequestDto("Too risky");

        mockMvc.perform(post("/api/recovery-cases/{id}/reject", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void nonexistentCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        RecoveryCaseController.RejectRequestDto req = new RecoveryCaseController.RejectRequestDto("Too risky");

        doThrow(new IllegalArgumentException("No pending action found")).when(executionService)
                .rejectActionByCase(eq(caseId), any());

        mockMvc.perform(post("/api/recovery-cases/{id}/reject", caseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
    @Test
    void investigateCaseSuccess() throws Exception {
        UUID caseId = UUID.randomUUID();
        com.secureasset.backend.dto.RecoveryCaseDetailDto mockDto = mock(com.secureasset.backend.dto.RecoveryCaseDetailDto.class);
        
        when(recoveryCaseService.investigateCase(caseId)).thenReturn(mockDto);

        mockMvc.perform(post("/api/recovery-cases/{id}/investigate", caseId))
                .andExpect(status().isOk());
    }
}
