package com.secureasset.backend.dto;

import java.math.BigDecimal;

public record DashboardMetricsDto(
        long transactionsAnalyzed,
        long recoveryOpportunities,
        long highPriorityCases,
        long mediumPriorityCases,
        long lowPriorityCases,
        BigDecimal revenueAtRisk,
        BigDecimal potentiallyRecoverable,
        BigDecimal recoveredRevenue,
        BigDecimal recoveryRate,
        String currency
) {}
