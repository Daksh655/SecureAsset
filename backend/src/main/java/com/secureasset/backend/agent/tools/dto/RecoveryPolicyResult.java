package com.secureasset.backend.agent.tools.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecoveryPolicyResult(
        int maxRecoveryAttempts,
        BigDecimal maxAutoRecoveryAmount,
        boolean paymentLinkAllowed,
        boolean retryPaymentAllowed,
        List<String> retryAllowedFailureReasons,
        List<String> retryDisallowedFailureReasons,
        int cooldownHours,
        BigDecimal humanApprovalRequiredAboveAmount
) {}
