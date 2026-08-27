package com.secureasset.backend.repository;

import com.secureasset.backend.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Modifying
    @Query("DELETE FROM Order o WHERE o.customer.dataset.id = :datasetId")
    void deleteByCustomerDatasetId(@Param("datasetId") UUID datasetId);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    boolean existsByRazorpayOrderId(String razorpayOrderId);

    List<Order> findByCustomerId(UUID customerId);

    List<Order> findByStatus(Order.OrderStatus status);
}