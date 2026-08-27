package com.secureasset.backend.service;

import com.secureasset.backend.entity.DemoDataset;
import com.secureasset.backend.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class DatasetService {

    public static final UUID DEMO_DATASET_ID = UUID.fromString("d3e0d3e0-0000-0000-0000-000000000000");

    private final DemoDatasetRepository datasetRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final AuditLogRepository auditLogRepository;
    private final SyntheticDataGeneratorService generatorService;

    public DatasetService(
            DemoDatasetRepository datasetRepository,
            CustomerRepository customerRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            RecoveryCaseRepository recoveryCaseRepository,
            RecoveryActionRepository recoveryActionRepository,
            AuditLogRepository auditLogRepository,
            SyntheticDataGeneratorService generatorService) {
        this.datasetRepository = datasetRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.auditLogRepository = auditLogRepository;
        this.generatorService = generatorService;
    }

    @Transactional
    public void generateDataset(String sizePreset) {
        if (datasetRepository.existsById(DEMO_DATASET_ID)) {
            throw new IllegalStateException("An active demo dataset already exists or an operation is in progress.");
        }

        int numCustomers, numOrders, numPayments;
        switch (sizePreset.toUpperCase()) {
            case "SMALL":
                numCustomers = 50; numOrders = 100; numPayments = 150; break;
            case "MEDIUM":
                numCustomers = 100; numOrders = 200; numPayments = 300; break;
            case "LARGE":
                numCustomers = 500; numOrders = 1000; numPayments = 1500; break;
            default:
                throw new IllegalArgumentException("Invalid size preset. Use SMALL, MEDIUM, or LARGE.");
        }

        DemoDataset dataset = new DemoDataset();
        dataset.setId(DEMO_DATASET_ID);
        dataset.setStatus(DemoDataset.Status.GENERATING);
        dataset.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        
        try {
            datasetRepository.saveAndFlush(dataset);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Concurrent generation detected.");
        }

        generatorService.generateDataset(dataset, numCustomers, numOrders, numPayments, 42L);

        dataset.setStatus(DemoDataset.Status.ACTIVE);
        datasetRepository.save(dataset);
    }

    @Transactional
    public void resetDataset() {
        DemoDataset dataset = datasetRepository.findById(DEMO_DATASET_ID)
                .orElseThrow(() -> new IllegalStateException("No active demo dataset to reset."));
        
        if (dataset.getStatus() != DemoDataset.Status.ACTIVE) {
            throw new IllegalStateException("Dataset is currently in use or generating.");
        }

        dataset.setStatus(DemoDataset.Status.RESETTING);
        datasetRepository.saveAndFlush(dataset);

        auditLogRepository.deleteByDatasetId(DEMO_DATASET_ID);
        recoveryActionRepository.deleteByRecoveryCaseCustomerDatasetId(DEMO_DATASET_ID);
        recoveryCaseRepository.deleteByCustomerDatasetId(DEMO_DATASET_ID);
        paymentRepository.deleteByCustomerDatasetId(DEMO_DATASET_ID);
        orderRepository.deleteByCustomerDatasetId(DEMO_DATASET_ID);
        customerRepository.deleteByDatasetId(DEMO_DATASET_ID);
        datasetRepository.deleteById(DEMO_DATASET_ID);
    }
}
