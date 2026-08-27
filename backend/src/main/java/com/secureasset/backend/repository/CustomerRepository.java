package com.secureasset.backend.repository;

import com.secureasset.backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Modifying
    @Query("DELETE FROM Customer c WHERE c.dataset.id = :datasetId")
    void deleteByDatasetId(@Param("datasetId") UUID datasetId);

    long countByDatasetId(UUID datasetId);

    Optional<Customer> findByRazorpayCustomerId(String razorpayCustomerId);

    boolean existsByRazorpayCustomerId(String razorpayCustomerId);
}