package com.secureasset.backend.service;

import com.secureasset.backend.entity.Customer;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.entity.RecoveryCase;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RevenueRiskEvaluationServiceTest {

    @Mock
    private RevenueRiskService revenueRiskService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RecoveryCaseRepository recoveryCaseRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @InjectMocks
    private RevenueRiskEvaluationService service;

    @Captor
    private ArgumentCaptor<RecoveryCase> caseCaptor;

    private Customer customer;
    private Order order;
    private Payment failedPayment;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(UUID.randomUUID());

        order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomer(customer);

        failedPayment = new Payment();
        failedPayment.setId(UUID.randomUUID());
        failedPayment.setCustomer(customer);
        failedPayment.setOrder(order);
        failedPayment.setAmount(new BigDecimal("1000.00"));
        failedPayment.setStatus(Payment.PaymentStatus.FAILED);
        failedPayment.setFailureReason(Payment.FailureReason.BANK_DECLINE);
        failedPayment.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void testEligibleFailedPaymentCreatesCase() {
        when(paymentRepository.findByStatus(eq(Payment.PaymentStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedPayment)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
                
        when(recoveryCaseRepository.existsByOrderIdAndStatusIn(eq(order.getId()), anyList())).thenReturn(false);
        when(recoveryCaseRepository.countByOrderId(order.getId())).thenReturn(0L);
        when(paymentRepository.countByCustomerIdAndStatus(customer.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(0L);
        when(paymentRepository.existsByOrderIdAndStatus(order.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(false);
        when(paymentRepository.countByOrderIdAndStatus(order.getId(), Payment.PaymentStatus.FAILED)).thenReturn(1L);

        RevenueRiskService.AssessmentResult result = new RevenueRiskService.AssessmentResult();
        result.setEligible(true);
        result.setScore(85);
        result.setPriority("HIGH");
        when(revenueRiskService.evaluateCandidate(any(), any())).thenReturn(result);

        service.evaluateFailedPayments();

        verify(recoveryCaseRepository).save(caseCaptor.capture());
        RecoveryCase savedCase = caseCaptor.getValue();
        
        assertEquals(customer, savedCase.getCustomer());
        assertEquals(order, savedCase.getOrder());
        assertEquals(failedPayment, savedCase.getPayment());
        assertEquals(RecoveryCase.ProblemType.PAYMENT_FAILURE, savedCase.getProblemType());
        assertEquals(new BigDecimal("1000.00"), savedCase.getRiskAmount());
        assertEquals(85, savedCase.getRecoveryScore());
        assertEquals(RecoveryCase.Priority.HIGH, savedCase.getPriority());
        assertEquals(RecoveryCase.Eligibility.ELIGIBLE, savedCase.getEligibility());
        assertEquals(RecoveryCase.Status.NEW, savedCase.getStatus());
        assertEquals(RecoveryCase.AgentStatus.NOT_ANALYZED, savedCase.getAgentStatus());
        assertNotNull(savedCase.getDetectedAt());
    }

    @Test
    void testAlreadyRecoveredPaymentDoesNotCreateCase() {
        when(paymentRepository.findByStatus(eq(Payment.PaymentStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedPayment)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
                
        when(recoveryCaseRepository.existsByOrderIdAndStatusIn(eq(order.getId()), anyList())).thenReturn(false);
        when(recoveryCaseRepository.countByOrderId(order.getId())).thenReturn(0L);
        when(paymentRepository.countByCustomerIdAndStatus(customer.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(0L);
        // Simulate already recovered
        when(paymentRepository.existsByOrderIdAndStatus(order.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(true);

        RevenueRiskService.AssessmentResult result = new RevenueRiskService.AssessmentResult();
        result.setEligible(false);
        when(revenueRiskService.evaluateCandidate(any(), any())).thenReturn(result);

        service.evaluateFailedPayments();

        verify(recoveryCaseRepository, never()).save(any());
    }

    @Test
    void testDuplicateActiveCaseDoesNotCreateAnotherCase() {
        when(paymentRepository.findByStatus(eq(Payment.PaymentStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedPayment)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
                
        // Simulate active duplicate
        when(recoveryCaseRepository.existsByOrderIdAndStatusIn(eq(order.getId()), anyList())).thenReturn(true);
        when(recoveryCaseRepository.countByOrderId(order.getId())).thenReturn(1L);
        when(paymentRepository.countByCustomerIdAndStatus(customer.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(0L);
        when(paymentRepository.existsByOrderIdAndStatus(order.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(false);

        RevenueRiskService.AssessmentResult result = new RevenueRiskService.AssessmentResult();
        result.setEligible(false);
        when(revenueRiskService.evaluateCandidate(any(), any())).thenReturn(result);

        service.evaluateFailedPayments();

        verify(recoveryCaseRepository, never()).save(any());
    }

    @Test
    void testIneligiblePaymentDoesNotCreateCase() {
        when(paymentRepository.findByStatus(eq(Payment.PaymentStatus.FAILED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failedPayment)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
                
        when(recoveryCaseRepository.existsByOrderIdAndStatusIn(eq(order.getId()), anyList())).thenReturn(false);
        when(recoveryCaseRepository.countByOrderId(order.getId())).thenReturn(0L);
        when(paymentRepository.countByCustomerIdAndStatus(customer.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(0L);
        when(paymentRepository.existsByOrderIdAndStatus(order.getId(), Payment.PaymentStatus.CAPTURED)).thenReturn(false);

        RevenueRiskService.AssessmentResult result = new RevenueRiskService.AssessmentResult();
        result.setEligible(false);
        when(revenueRiskService.evaluateCandidate(any(), any())).thenReturn(result);

        service.evaluateFailedPayments();

        verify(recoveryCaseRepository, never()).save(any());
    }
}
