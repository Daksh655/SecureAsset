package com.secureasset.backend.controller;

import com.secureasset.backend.dto.GlobalAuditLogDto;
import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.service.AuditLogQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogControllerTest {

    private MockMvc mockMvc;
    private AuditLogQueryService auditLogQueryService;

    @BeforeEach
    void setUp() {
        auditLogQueryService = Mockito.mock(AuditLogQueryService.class);
        AuditLogController controller = new AuditLogController(auditLogQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnPaginatedAuditLogs() throws Exception {
        UUID logId = UUID.randomUUID();
        GlobalAuditLogDto dto = new GlobalAuditLogDto(
                logId, "CASE_CREATED", "SYSTEM", null, "Case was created", true, OffsetDateTime.now(), UUID.randomUUID(), null
        );

        PageResponse<GlobalAuditLogDto> pageResponse = new PageResponse<>(
                List.of(dto), 0, 20, 1, 1
        );

        Mockito.when(auditLogQueryService.searchAuditLogs(eq("CASE_CREATED"), any(), anyInt(), anyInt()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/audit-logs")
                        .param("eventType", "CASE_CREATED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.content[0].eventType").value("CASE_CREATED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
