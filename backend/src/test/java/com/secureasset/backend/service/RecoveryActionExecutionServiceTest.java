package com.secureasset.backend.service;

import com.secureasset.backend.entity.AuditLog;
import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.integration.RazorpayPaymentService;
import com.secureasset.backend.repository.AuditLogRepository;
import com.secureasset.backend.repository.RecoveryActionRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecoveryActionExecutionServiceTest {

    private RecoveryCaseRepository caseRepository;
    private RecoveryActionRepository actionRepository;
    private AuditLogRepository auditLogRepository;
    private RazorpayPaymentService razorpayPaymentService;
    private RecoveryActionExecutionService service;

    @BeforeEach
    void setUp() {
        caseRepository = mock(RecoveryCaseRepository.class);
        actionRepository = mock(RecoveryActionRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        razorpayPaymentService = mock(RazorpayPaymentService.class);
        service = new RecoveryActionExecutionService(actionRepository, caseRepository, auditLogRepository, razorpayPaymentService);

        when(actionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));
        when(caseRepository.save(any(RecoveryCase.class))).thenAnswer(i -> i.getArgument(0));
        
        service.setSelf(service);
    }

    @Test
    void validPaymentLinkActionSucceeds() {
        UUID caseId = UUID.randomUUID();
        RecoveryCase rc = new RecoveryCase();
        rc.setId(caseId);
        rc.setStatus(RecoveryCase.Status.ACTION_REQUIRED);
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(rc));
        when(actionRepository.findByRecoveryCaseId(caseId)).thenReturn(List.of());
        
        when(actionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

        // Propose
        RecoveryAction action = service.proposeAction(caseId, RecoveryAction.ActionType.CREATE_PAYMENT_LINK, new BigDecimal("7500.00"));
        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.PENDING); // CREATE_PAYMENT_LINK always requires approval

        // Execute prep
        UUID actionId = UUID.randomUUID();
        action.setId(actionId);
        when(actionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(razorpayPaymentService.createPaymentLink(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RazorpayPaymentService.PaymentLinkResult(true, "plink_123", "url", "ref", "created", new BigDecimal("7500"), "INR"));

        // Approve it first!
        service.approveAction(actionId);

        // Execute
        service.executeAction(actionId);

        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.SUCCESS);
        assertThat(action.getRazorpayReference()).isEqualTo("plink_123");
        assertThat(rc.getStatus()).isEqualTo(RecoveryCase.Status.EXECUTING);

        verify(auditLogRepository, atLeastOnce()).save(any(AuditLog.class));
    }

    @Test
    void cannotExecuteAutomaticallyIfAmountTooHigh() {
        UUID caseId = UUID.randomUUID();
        RecoveryCase rc = new RecoveryCase();
        rc.setId(caseId);
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(rc));
        when(actionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

        RecoveryAction action = service.proposeAction(caseId, RecoveryAction.ActionType.CREATE_PAYMENT_LINK, new BigDecimal("15000.00"));
        
        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.PENDING);
        assertThat(rc.getStatus()).isEqualTo(RecoveryCase.Status.PENDING_APPROVAL);

        UUID actionId = UUID.randomUUID();
        action.setId(actionId);
        when(actionRepository.findById(actionId)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.executeAction(actionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Action is not approved");
    }

    @Test
    void duplicateActiveActionBlocked() {
        UUID caseId = UUID.randomUUID();
        RecoveryCase rc = new RecoveryCase();
        rc.setId(caseId);
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(rc));
        
        RecoveryAction existing = new RecoveryAction();
        existing.setStatus(RecoveryAction.Status.PENDING);
        when(actionRepository.findByRecoveryCaseId(caseId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.proposeAction(caseId, RecoveryAction.ActionType.CREATE_PAYMENT_LINK, new BigDecimal("5000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");
    }

    @Test
    void alreadyRecoveredCaseBlocked() {
        UUID caseId = UUID.randomUUID();
        RecoveryCase rc = new RecoveryCase();
        rc.setId(caseId);
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setStatus(RecoveryCase.Status.RECOVERED);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(rc));

        assertThatThrownBy(() -> service.proposeAction(caseId, RecoveryAction.ActionType.CREATE_PAYMENT_LINK, new BigDecimal("5000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already recovered");
    }

    @Test
    void maxAttemptsBlocked() {
        UUID caseId = UUID.randomUUID();
        RecoveryCase rc = new RecoveryCase();
        rc.setId(caseId);
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(rc));
        
        RecoveryAction a1 = new RecoveryAction(); a1.setStatus(RecoveryAction.Status.FAILED);
        RecoveryAction a2 = new RecoveryAction(); a2.setStatus(RecoveryAction.Status.FAILED);
        when(actionRepository.findByRecoveryCaseId(caseId)).thenReturn(List.of(a1, a2));

        assertThatThrownBy(() -> service.proposeAction(caseId, RecoveryAction.ActionType.CREATE_PAYMENT_LINK, new BigDecimal("5000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum recovery attempts reached");
    }

    @Test
    void razorpay4xxFailsSafely() {
        UUID actionId = UUID.randomUUID();
        RecoveryAction action = new RecoveryAction();
        action.setId(actionId);
        action.setStatus(RecoveryAction.Status.APPROVED);
        action.setApprovalStatus(RecoveryAction.ApprovalStatus.APPROVED);
        action.setActionType(RecoveryAction.ActionType.CREATE_PAYMENT_LINK);
        action.setAmount(new BigDecimal("5000"));
        
        RecoveryCase rc = new RecoveryCase();
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        action.setRecoveryCase(rc);
        
        when(actionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(razorpayPaymentService.createPaymentLink(any(), any(), any(), any(), any(), any()))
                .thenReturn(RazorpayPaymentService.PaymentLinkResult.failure());

        service.executeAction(actionId);

        assertThat(action.getStatus()).isEqualTo(RecoveryAction.Status.FAILED);
        assertThat(rc.getStatus()).isEqualTo(RecoveryCase.Status.ACTION_REQUIRED);
    }

    @Test
    void repeatedExecutionIsIdempotent() {
        UUID actionId = UUID.randomUUID();
        RecoveryAction action = new RecoveryAction();
        action.setId(actionId);
        action.setStatus(RecoveryAction.Status.SUCCESS); // Already success
        
        when(actionRepository.findById(actionId)).thenReturn(Optional.of(action));
        
        service.executeAction(actionId);
        
        // Should just return, not call external service
        verify(razorpayPaymentService, never()).createPaymentLink(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidStateTransitionRejected() {
        UUID actionId = UUID.randomUUID();
        RecoveryAction action = new RecoveryAction();
        action.setId(actionId);
        action.setStatus(RecoveryAction.Status.APPROVED); // not pending
        
        when(actionRepository.findById(actionId)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.approveAction(actionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending approval");
    }

    @Test
    void testPersistenceOrdering() {
        UUID caseId = UUID.randomUUID();
        RecoveryCase rc = new RecoveryCase();
        rc.setId(caseId);
        rc.setEligibility(RecoveryCase.Eligibility.ELIGIBLE);
        rc.setStatus(RecoveryCase.Status.NEW);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(rc));

        RecoveryAction savedAction = new RecoveryAction();
        savedAction.setId(UUID.randomUUID());
        savedAction.setRecoveryCase(rc);
        when(actionRepository.save(any(RecoveryAction.class))).thenReturn(savedAction);
        when(caseRepository.save(any(RecoveryCase.class))).thenReturn(rc);

        service.proposeAction(caseId, RecoveryAction.ActionType.CREATE_PAYMENT_LINK, new BigDecimal("7500.00"));

        org.mockito.InOrder inOrder = inOrder(actionRepository, auditLogRepository);
        inOrder.verify(actionRepository).save(any(RecoveryAction.class));
        inOrder.verify(auditLogRepository).save(any(AuditLog.class));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog savedAudit = auditCaptor.getValue();
        
        assertThat(savedAudit.getRecoveryAction()).isSameAs(savedAction);
        assertThat(savedAudit.getRecoveryAction().getId()).isNotNull();
    }
}
