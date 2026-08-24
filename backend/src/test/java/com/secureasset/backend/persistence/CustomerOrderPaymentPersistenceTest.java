package com.secureasset.backend.persistence;

import com.secureasset.backend.entity.Customer;
import com.secureasset.backend.entity.Order;
import com.secureasset.backend.entity.Payment;
import com.secureasset.backend.repository.CustomerRepository;
import com.secureasset.backend.repository.OrderRepository;
import com.secureasset.backend.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CustomerOrderPaymentPersistenceTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldPersistAndReadCustomerOrderAndFailedPayment() {
        Customer customer = new Customer();
        customer.setName("SecureAsset Test Customer");
        customer.setEmail("test@secureasset.local");
        customer.setPhone("9999999999");
        customer.setCreatedAt(OffsetDateTime.now());
        customer.setUpdatedAt(OffsetDateTime.now());

        Customer savedCustomer = customerRepository.save(customer);

        Order order = new Order();
        order.setCustomer(savedCustomer);
        order.setAmount(new BigDecimal("7499.00"));
        order.setCurrency("INR");
        order.setStatus(Order.OrderStatus.ATTEMPTED);
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());

        Order savedOrder = orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(savedOrder);
        payment.setCustomer(savedCustomer);
        payment.setAmount(new BigDecimal("7499.00"));
        payment.setCurrency("INR");
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(Payment.FailureReason.TIMEOUT);
        payment.setMethod("UPI");
        payment.setAttemptNumber(1);
        payment.setCreatedAt(OffsetDateTime.now());
        payment.setFailedAt(OffsetDateTime.now());
        payment.setUpdatedAt(OffsetDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        Payment loadedPayment =
                paymentRepository.findById(savedPayment.getId()).orElseThrow();

        assertThat(loadedPayment.getAmount())
                .isEqualByComparingTo("7499.00");

        assertThat(loadedPayment.getStatus())
                .isEqualTo(Payment.PaymentStatus.FAILED);

        assertThat(loadedPayment.getFailureReason())
                .isEqualTo(Payment.FailureReason.TIMEOUT);

        assertThat(loadedPayment.getOrder().getId())
                .isEqualTo(savedOrder.getId());

        assertThat(loadedPayment.getCustomer().getId())
                .isEqualTo(savedCustomer.getId());
    }
}