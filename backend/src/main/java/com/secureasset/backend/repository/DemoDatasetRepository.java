package com.secureasset.backend.repository;

import com.secureasset.backend.entity.DemoDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DemoDatasetRepository extends JpaRepository<DemoDataset, UUID> {
    Optional<DemoDataset> findFirstByStatus(DemoDataset.Status status);

    boolean existsByStatus(DemoDataset.Status status);
}
