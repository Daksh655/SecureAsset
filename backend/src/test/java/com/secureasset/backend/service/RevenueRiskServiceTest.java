package com.secureasset.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RevenueRiskServiceTest {

    private RevenueRiskService service;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        service = new RevenueRiskService();
        now = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Test
    void testHighValueTimeout() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("7500.00"));
        context.setPreviousSuccessfulPayments(8); // Note: 8 payments gives history score 14. 
        context.setFailureReason("TIMEOUT");
        context.setEventTime(now.minusMinutes(10));
        context.setPreviousRecoveryAttempts(0);
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertTrue(result.isEligible());
        assertEquals("HIGH", result.getPriority());
        // Amount: 5000-9999 = 18
        // History: 6-9 = 14
        // Failure: TIMEOUT = 25
        // Recency: <15m = 17
        // Attempts: 0 = 20
        // Total = 18 + 14 + 25 + 17 + 20 = 94
        assertEquals(94, result.getScore());
    }
    
    @Test
    void testHighValueTimeoutPromptExactScore() {
        // To match the exact score 98 from the prompt, successful payments should be >= 10.
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("7500.00"));
        context.setPreviousSuccessfulPayments(12);
        context.setFailureReason("TIMEOUT");
        context.setEventTime(now.minusMinutes(10));
        context.setPreviousRecoveryAttempts(0);
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertTrue(result.isEligible());
        assertEquals("HIGH", result.getPriority());
        assertEquals(98, result.getScore());
    }

    @Test
    void testLowValueInsufficientFunds() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("299.00"));
        context.setPreviousSuccessfulPayments(0);
        context.setFailureReason("INSUFFICIENT_FUNDS");
        context.setEventTime(now.minusHours(20));
        context.setPreviousRecoveryAttempts(2);
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertTrue(result.isEligible());
        assertEquals("LOW", result.getPriority());
        // Amount: <500 = 5
        // History: 0 = 3
        // Failure: INSUFFICIENT_FUNDS = 4
        // Recency: 6-24h = 5
        // Attempts: 2 = 3
        // Total = 5 + 3 + 4 + 5 + 3 = 20
        assertEquals(20, result.getScore());
    }

    @Test
    void testAlreadyRecovered() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("1000.00"));
        context.setAlreadyRecovered(true);
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertFalse(result.isEligible());
        assertEquals(0, result.getScore());
        assertEquals("INELIGIBLE", result.getPriority());
    }

    @Test
    void testTooManyPreviousRecoveryAttempts() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("1000.00"));
        context.setPreviousRecoveryAttempts(3); // > max of 2
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertFalse(result.isEligible());
        assertEquals(0, result.getScore());
        assertEquals("INELIGIBLE", result.getPriority());
    }

    @Test
    void testInvalidAmount() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("0.00"));
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertFalse(result.isEligible());
        assertEquals(0, result.getScore());
        assertEquals("INELIGIBLE", result.getPriority());
    }

    @Test
    void testDuplicateActiveRecoveryCase() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("1000.00"));
        context.setDuplicateActiveCaseExists(true);
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertFalse(result.isEligible());
        assertEquals(0, result.getScore());
        assertEquals("INELIGIBLE", result.getPriority());
    }

    @Test
    void testMissingContext() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("1000.00"));
        context.setMissingContext(true);
        
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertFalse(result.isEligible());
        assertEquals(0, result.getScore());
        assertEquals("INELIGIBLE", result.getPriority());
    }
    
    @Test
    void testScoreCappedAt100() {
        RevenueRiskService.CandidateContext context = new RevenueRiskService.CandidateContext();
        context.setAmount(new BigDecimal("20000.00")); // 20
        context.setPreviousSuccessfulPayments(20); // 18
        context.setFailureReason("TIMEOUT"); // 25
        context.setEventTime(now.minusMinutes(1)); // 17
        context.setPreviousRecoveryAttempts(0); // 20
        // sum = 100
        
        // Let's modify slightly to go over 100 if it was possible (actually max is 20+18+25+17+20 = 100).
        // Wait, 20+18+25+17+20 = 100 exactly!
        // Is it possible to go over 100? No, 20+18+25+17+20 = 100 is the mathematical max of the rules.
        // So cap is always respected inherently by the rules. We still test it works up to 100.
        RevenueRiskService.AssessmentResult result = service.evaluateCandidate(context, now);
        
        assertTrue(result.isEligible());
        assertEquals("HIGH", result.getPriority());
        assertEquals(100, result.getScore());
    }
}
