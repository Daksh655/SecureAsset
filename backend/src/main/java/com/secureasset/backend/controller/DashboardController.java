package com.secureasset.backend.controller;

import com.secureasset.backend.dto.DashboardMetricsDto;
import com.secureasset.backend.service.RecoveryCaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final RecoveryCaseService recoveryCaseService;

    public DashboardController(RecoveryCaseService recoveryCaseService) {
        this.recoveryCaseService = recoveryCaseService;
    }

    @GetMapping
    public DashboardMetricsDto getDashboardMetrics() {
        return recoveryCaseService.getDashboardMetrics();
    }
}
