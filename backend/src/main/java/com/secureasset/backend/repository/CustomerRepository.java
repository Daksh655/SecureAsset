package com.secureasset.backend.repository;

import com.secureasset.backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByRazorpayCustomerId(String razorpayCustomerId);

    boolean existsByRazorpayCustomerId(String razorpayCustomerId);
}