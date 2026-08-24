package com.secureasset.backend.service;
// Given these facts, what is the score?
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class RevenueRiskService {

    public static class CandidateContext {
        private BigDecimal amount;
        private int previousSuccessfulPayments;
        private String failureReason;
        private OffsetDateTime eventTime;
        private int previousRecoveryAttempts;
        private boolean alreadyRecovered;
        private boolean duplicateActiveCaseExists;
        private boolean missingContext;
        private int maxRecoveryAttempts = 2; // Default configurable limit

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public int getPreviousSuccessfulPayments() { return previousSuccessfulPayments; }
        public void setPreviousSuccessfulPayments(int previousSuccessfulPayments) { this.previousSuccessfulPayments = previousSuccessfulPayments; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

        public OffsetDateTime getEventTime() { return eventTime; }
        public void setEventTime(OffsetDateTime eventTime) { this.eventTime = eventTime; }

        public int getPreviousRecoveryAttempts() { return previousRecoveryAttempts; }
        public void setPreviousRecoveryAttempts(int previousRecoveryAttempts) { this.previousRecoveryAttempts = previousRecoveryAttempts; }

        public boolean isAlreadyRecovered() { return alreadyRecovered; }
        public void setAlreadyRecovered(boolean alreadyRecovered) { this.alreadyRecovered = alreadyRecovered; }

        public boolean isDuplicateActiveCaseExists() { return duplicateActiveCaseExists; }
        public void setDuplicateActiveCaseExists(boolean duplicateActiveCaseExists) { this.duplicateActiveCaseExists = duplicateActiveCaseExists; }

        public boolean isMissingContext() { return missingContext; }
        public void setMissingContext(boolean missingContext) { this.missingContext = missingContext; }

        public int getMaxRecoveryAttempts() { return maxRecoveryAttempts; }
        public void setMaxRecoveryAttempts(int maxRecoveryAttempts) { this.maxRecoveryAttempts = maxRecoveryAttempts; }
    }

    public static class AssessmentResult {
        private boolean eligible;
        private int score;
        private String priority;

        public boolean isEligible() { return eligible; }
        public void setEligible(boolean eligible) { this.eligible = eligible; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }

    public AssessmentResult evaluateCandidate(CandidateContext context, OffsetDateTime now) {
        AssessmentResult result = new AssessmentResult();
        
        // 1. Eligibility Check
        if (context.isMissingContext() ||
            context.isAlreadyRecovered() ||
            context.isDuplicateActiveCaseExists() ||
            context.getPreviousRecoveryAttempts() > context.getMaxRecoveryAttempts() ||
            context.getAmount() == null ||
            context.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            
            result.setEligible(false);
            result.setScore(0);
            result.setPriority("INELIGIBLE");
            return result;
        }

        result.setEligible(true);

        // 2. Score Calculation
        int score = 0;
        
        // Amount
        BigDecimal amt = context.getAmount();
        if (amt.compareTo(new BigDecimal("500")) < 0) {
            score += 5;
        } else if (amt.compareTo(new BigDecimal("2000")) < 0) {
            score += 10;
        } else if (amt.compareTo(new BigDecimal("5000")) < 0) {
            score += 15;
        } else if (amt.compareTo(new BigDecimal("10000")) < 0) {
            score += 18;
        } else {
            score += 20;
        }

        // Customer History
        int hist = context.getPreviousSuccessfulPayments();
        if (hist == 0) {
            score += 3;
        } else if (hist <= 2) {
            score += 7;
        } else if (hist <= 5) {
            score += 10;
        } else if (hist <= 9) {
            score += 14;
        } else {
            score += 18;
        }

        // Failure recoverability
        String reason = context.getFailureReason();
        if (reason == null) reason = "UNKNOWN";
        switch (reason.toUpperCase()) {
            case "TIMEOUT":
            case "NETWORK_ERROR":
                score += 25;
                break;
            case "CUSTOMER_CANCELLED":
                score += 8;
                break;
            case "BANK_DECLINE":
                score += 5;
                break;
            case "INSUFFICIENT_FUNDS":
                score += 4;
                break;
            case "UNKNOWN":
            default:
                score += 15;
                break;
        }

        // Recency
        Duration age = Duration.between(context.getEventTime(), now);
        long minutes = age.toMinutes();
        if (minutes < 15) {
            score += 17;
        } else if (minutes <= 60) {
            score += 12;
        } else if (minutes <= 360) {
            score += 8;
        } else if (minutes <= 1440) {
            score += 5;
        } else {
            score += 2;
        }

        // Recovery History
        int attempts = context.getPreviousRecoveryAttempts();
        if (attempts == 0) {
            score += 20;
        } else if (attempts == 1) {
            score += 10;
        } else if (attempts == 2) {
            score += 3;
        } else {
            score += 0;
        }

        // Cap score at 100
        if (score > 100) {
            score = 100;
        }
        result.setScore(score);

        // Priority
        if (score >= 80) {
            result.setPriority("HIGH");
        } else if (score >= 50) {
            result.setPriority("MEDIUM");
        } else if (score >= 1) {
            result.setPriority("LOW");
        } else {
            result.setPriority("INELIGIBLE");
        }

        return result;
    }
}
