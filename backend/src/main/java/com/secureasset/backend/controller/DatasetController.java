package com.secureasset.backend.controller;

import com.secureasset.backend.entity.DemoDataset;
import com.secureasset.backend.repository.DemoDatasetRepository;
import com.secureasset.backend.repository.PaymentRepository;
import com.secureasset.backend.repository.RecoveryCaseRepository;
import com.secureasset.backend.service.DatasetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dataset")
public class DatasetController {

    private final DatasetService datasetService;
    private final DemoDatasetRepository datasetRepository;
    private final PaymentRepository paymentRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;

    public DatasetController(
            DatasetService datasetService,
            DemoDatasetRepository datasetRepository,
            PaymentRepository paymentRepository,
            RecoveryCaseRepository recoveryCaseRepository) {
        this.datasetService = datasetService;
        this.datasetRepository = datasetRepository;
        this.paymentRepository = paymentRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Optional<DemoDataset> activeOpt = datasetRepository.findById(DatasetService.DEMO_DATASET_ID);
        
        if (activeOpt.isPresent()) {
            DemoDataset ds = activeOpt.get();
            long transactionCount = paymentRepository.countByCustomerDatasetId(DatasetService.DEMO_DATASET_ID);
            long recoveryOpportunityCount = recoveryCaseRepository.countByCustomerDatasetId(DatasetService.DEMO_DATASET_ID);
            
            return ResponseEntity.ok(Map.of(
                    "active", ds.getStatus() == DemoDataset.Status.ACTIVE,
                    "datasetId", ds.getId(),
                    "generatedAt", ds.getCreatedAt(),
                    "status", ds.getStatus(),
                    "transactionCount", transactionCount,
                    "recoveryOpportunityCount", recoveryOpportunityCount
            ));
        } else {
            return ResponseEntity.ok(Map.of("active", false));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateDataset(@RequestBody Map<String, String> body) {
        String size = body.getOrDefault("size", "MEDIUM");
        try {
            datasetService.generateDataset(size);
            return ResponseEntity.ok(Map.of("message", "Dataset generated successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetDataset() {
        try {
            datasetService.resetDataset();
            return ResponseEntity.ok(Map.of("message", "Dataset reset successfully"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
