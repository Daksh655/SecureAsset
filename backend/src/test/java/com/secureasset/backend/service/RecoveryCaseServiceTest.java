package com.secureasset.backend.service;

import com.secureasset.backend.dto.DashboardMetricsDto;
import com.secureasset.backend.dto.PageResponse;
import com.secureasset.backend.dto.RecoveryCaseDetailDto;
import com.secureasset.backend.dto.RecoveryCaseSummaryDto;
import com.secureasset.backend.entity.Customer;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryCaseServiceTest {

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;
    @Mock
    private RecoveryActionRepository recoveryActionRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private RecoveryCaseService service;

    private RecoveryCase recoveryCase;

    @BeforeEach
    void setUp() {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setName("Test");
        
        recoveryCase = new RecoveryCase();
        recoveryCase.setId(UUID.randomUUID());
        recoveryCase.setCustomer(c);
        recoveryCase.setPriority(RecoveryCase.Priority.HIGH);
        recoveryCase.setStatus(RecoveryCase.Status.NEW);
        recoveryCase.setProblemType(RecoveryCase.ProblemType.PAYMENT_FAILURE);
        recoveryCase.setAgentStatus(RecoveryCase.AgentStatus.NOT_ANALYZED);
        recoveryCase.setRiskAmount(new BigDecimal("1000.00"));
    }

    @Test
    void testSearchCasesPaginatedAndHighPriority() {
        when(recoveryCaseRepository.searchCases(
                eq(RecoveryCase.Priority.HIGH), any(), any(), any(), any(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(recoveryCase)));

        PageResponse<RecoveryCaseSummaryDto> response = service.searchCases(
                "HIGH", null, null, null, null, null, 0, 20
        );

        assertEquals(1, response.content().size());
        assertEquals("HIGH", response.content().get(0).priority());
        assertEquals(0, response.page());
        assertEquals(1, response.totalElements());
    }

    @Test
    void testGetCaseDetailsSuccess() {
        when(recoveryCaseRepository.findById(recoveryCase.getId())).thenReturn(Optional.of(recoveryCase));

        RecoveryCaseDetailDto dto = service.getCaseDetails(recoveryCase.getId());

        assertNotNull(dto);
        assertEquals("HIGH", dto.priority());
    }

    @Test
    void testGetCaseDetailsNotFound() {
        when(recoveryCaseRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.getCaseDetails(UUID.randomUUID()));
    }

    @Test
    void testDashboardMetricsCalculation() {
        when(paymentRepository.count()).thenReturn(100L);
        when(recoveryCaseRepository.count()).thenReturn(10L);
        when(recoveryCaseRepository.countByPriority(RecoveryCase.Priority.HIGH)).thenReturn(5L);
        when(recoveryCaseRepository.sumRiskAmount()).thenReturn(new BigDecimal("10000.00"));
        
        when(recoveryCaseRepository.sumRiskAmountByStatuses(any()))
                .thenReturn(new BigDecimal("8000.00")) // active
                .thenReturn(new BigDecimal("2000.00")); // recovered

        DashboardMetricsDto dto = service.getDashboardMetrics();

        assertEquals(100L, dto.transactionsAnalyzed());
        assertEquals(10L, dto.recoveryOpportunities());
        assertEquals(5L, dto.highPriorityCases());
        assertEquals(new BigDecimal("10000.00"), dto.revenueAtRisk());
        assertEquals(new BigDecimal("8000.00"), dto.potentiallyRecoverable());
        assertEquals(new BigDecimal("2000.00"), dto.recoveredRevenue());
        // Rate = 2000 / 8000 = 25%
        assertEquals(new BigDecimal("25.00"), dto.recoveryRate());
    }
    
    @Test
    void testGetActions() {
        when(recoveryCaseRepository.existsById(recoveryCase.getId())).thenReturn(true);
        when(recoveryActionRepository.findByRecoveryCaseIdOrderByRequestedAtDesc(recoveryCase.getId()))
                .thenReturn(Collections.emptyList());
        
        assertTrue(service.getCaseActions(recoveryCase.getId()).isEmpty());
    }
    
    @Test
    void testGetAudit() {
        when(recoveryCaseRepository.existsById(recoveryCase.getId())).thenReturn(true);
        when(auditLogRepository.findByRecoveryCaseIdOrderByCreatedAtAsc(recoveryCase.getId()))
                .thenReturn(Collections.emptyList());
        
        assertTrue(service.getCaseAuditLogs(recoveryCase.getId()).isEmpty());
    }
}
