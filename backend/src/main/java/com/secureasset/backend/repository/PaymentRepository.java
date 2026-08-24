package com.secureasset.backend.repository;

import com.secureasset.backend.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    boolean existsByRazorpayPaymentId(String razorpayPaymentId);

    List<Payment> findByCustomerId(UUID customerId);

    List<Payment> findByOrderId(UUID orderId);

    List<Payment> findByStatus(Payment.PaymentStatus status);

    List<Payment> findByCustomerIdAndStatus(
            UUID customerId,
            Payment.PaymentStatus status
    );

    List<Payment> findByStatusOrderByCreatedAtDesc(
            Payment.PaymentStatus status
    );

    Page<Payment> findByStatus(Payment.PaymentStatus status, Pageable pageable);

    long countByCustomerIdAndStatus(UUID customerId, Payment.PaymentStatus status);

    boolean existsByOrderIdAndStatus(UUID orderId, Payment.PaymentStatus status);

    long countByOrderIdAndStatus(UUID orderId, Payment.PaymentStatus status);
}