package com.secureasset.backend.agent.dto;

import com.secureasset.backend.entity.RecoveryCase;
import java.util.List;

public record AgentRecommendation(
        RecoveryCase.AgentRecommendation action,
        Integer confidence,
        String reason,
        List<String> evidence
) {}
