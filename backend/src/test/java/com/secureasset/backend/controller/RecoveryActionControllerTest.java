package com.secureasset.backend.controller;

import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.dto.RecoveryActionSummaryDto;
import com.secureasset.backend.service.RecoveryActionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecoveryActionControllerTest {

    private MockMvc mockMvc;
    private RecoveryActionQueryService recoveryActionQueryService;

    @BeforeEach
    void setUp() {
        recoveryActionQueryService = Mockito.mock(RecoveryActionQueryService.class);
        RecoveryActionController controller = new RecoveryActionController(recoveryActionQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnPaginatedActions() throws Exception {
        UUID actionId = UUID.randomUUID();
        RecoveryActionSummaryDto dto = new RecoveryActionSummaryDto(
                actionId, UUID.randomUUID(), "CREATE_PAYMENT_LINK", BigDecimal.TEN, "PENDING", "PENDING", null, OffsetDateTime.now(), null, null, null, null, null
        );

        PageResponse<RecoveryActionSummaryDto> pageResponse = new PageResponse<>(
                List.of(dto), 0, 20, 1, 1
        );

        Mockito.when(recoveryActionQueryService.searchActions(eq("PENDING"), eq("PENDING"), eq(null), anyInt(), anyInt()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/recovery-actions")
                        .param("status", "PENDING")
                        .param("approvalStatus", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(actionId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
