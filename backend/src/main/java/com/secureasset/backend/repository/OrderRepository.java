package com.secureasset.backend.repository;

import com.secureasset.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    boolean existsByRazorpayOrderId(String razorpayOrderId);

    List<Order> findByCustomerId(UUID customerId);

    List<Order> findByStatus(Order.OrderStatus status);
}