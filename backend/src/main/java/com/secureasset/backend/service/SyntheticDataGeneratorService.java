package com.secureasset.backend.service;

import com.secureasset.backend.entity.*;
import com.secureasset.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class SyntheticDataGeneratorService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public SyntheticDataGeneratorService(
            CustomerRepository customerRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void generateDataset(DemoDataset dataset, int numCustomers, int numOrders, int numPayments, long seed) {
        Random random = new Random(seed);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < numCustomers; i++) {
            Customer c = new Customer();
            c.setDataset(dataset);
            c.setRazorpayCustomerId("cust_" + UUID.randomUUID().toString().substring(0, 12));
            c.setName("Customer " + i);
            c.setEmail("customer" + i + "@example.com");
            c.setPhone("+91" + (9000000000L + random.nextInt(1000000000)));
            c.setCreatedAt(randomDate(now.minusDays(30), now, random));
            c.setUpdatedAt(c.getCreatedAt());
            customers.add(c);
        }
        customers = customerRepository.saveAll(customers);

        List<Customer> scenarioCusts = numCustomers >= 6 ? customers.subList(0, 6) : customers;
        List<Order> orders = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();

        int[] orderCounter = {0};
        int[] paymentCounter = {0};

        // 1. high-value timeout
        if (orderCounter[0] < numOrders && scenarioCusts.size() > 0) {
            Customer c = scenarioCusts.get(0);
            Order o = addOrder(c, new BigDecimal("15000.00"), Order.OrderStatus.FAILED, randomDate(c.getCreatedAt(), now, random), orders, orderCounter);
            if (paymentCounter[0] < numPayments) {
                OffsetDateTime pTime = randomDate(o.getCreatedAt(), now, random);
                addPayment(o, c, new BigDecimal("15000.00"), Payment.PaymentStatus.FAILED, Payment.FailureReason.TIMEOUT, "upi", 1, pTime, pTime.plusSeconds(10), null, payments, paymentCounter);
            }
        }

        // 2. low-value insufficient-funds
        if (orderCounter[0] < numOrders && scenarioCusts.size() > 1) {
            Customer c = scenarioCusts.get(1);
            Order o = addOrder(c, new BigDecimal("299.00"), Order.OrderStatus.FAILED, randomDate(c.getCreatedAt(), now, random), orders, orderCounter);
            if (paymentCounter[0] < numPayments) {
                OffsetDateTime pTime = randomDate(o.getCreatedAt(), now, random);
                addPayment(o, c, new BigDecimal("299.00"), Payment.PaymentStatus.FAILED, Payment.FailureReason.INSUFFICIENT_FUNDS, "card", 1, pTime, pTime.plusSeconds(10), null, payments, paymentCounter);
            }
        }

        // 3. repeated payment failure
        if (orderCounter[0] < numOrders && scenarioCusts.size() > 2) {
            Customer c = scenarioCusts.get(2);
            Order o = addOrder(c, new BigDecimal("2500.00"), Order.OrderStatus.FAILED, randomDate(c.getCreatedAt(), now, random), orders, orderCounter);
            OffsetDateTime pTime1 = randomDate(o.getCreatedAt(), now, random);
            if (paymentCounter[0] < numPayments) {
                addPayment(o, c, new BigDecimal("2500.00"), Payment.PaymentStatus.FAILED, Payment.FailureReason.BANK_DECLINE, "upi", 1, pTime1, pTime1.plusSeconds(5), null, payments, paymentCounter);
            }
            if (paymentCounter[0] < numPayments) {
                OffsetDateTime pTime2 = pTime1.plusMinutes(15);
                addPayment(o, c, new BigDecimal("2500.00"), Payment.PaymentStatus.FAILED, Payment.FailureReason.NETWORK_ERROR, "upi", 2, pTime2, pTime2.plusSeconds(5), null, payments, paymentCounter);
            }
            if (paymentCounter[0] < numPayments) {
                OffsetDateTime pTime3 = pTime1.plusHours(2);
                addPayment(o, c, new BigDecimal("2500.00"), Payment.PaymentStatus.FAILED, Payment.FailureReason.TIMEOUT, "upi", 3, pTime3, pTime3.plusSeconds(5), null, payments, paymentCounter);
            }
        }

        // 4. already recovered payment
        if (orderCounter[0] < numOrders && scenarioCusts.size() > 3) {
            Customer c = scenarioCusts.get(3);
            Order o = addOrder(c, new BigDecimal("4000.00"), Order.OrderStatus.PAID, randomDate(c.getCreatedAt(), now, random), orders, orderCounter);
            OffsetDateTime pTime1 = randomDate(o.getCreatedAt(), now, random);
            if (paymentCounter[0] < numPayments) {
                addPayment(o, c, new BigDecimal("4000.00"), Payment.PaymentStatus.FAILED, Payment.FailureReason.BANK_DECLINE, "card", 1, pTime1, pTime1.plusSeconds(5), null, payments, paymentCounter);
            }
            if (paymentCounter[0] < numPayments) {
                OffsetDateTime pTime2 = pTime1.plusHours(1);
                addPayment(o, c, new BigDecimal("4000.00"), Payment.PaymentStatus.CAPTURED, null, "card", 2, pTime2, null, pTime2.plusSeconds(5), payments, paymentCounter);
            }
        }

        // 5. checkout abandonment
        if (orderCounter[0] < numOrders && scenarioCusts.size() > 4) {
            Customer c = scenarioCusts.get(4);
            addOrder(c, new BigDecimal("8000.00"), Order.OrderStatus.ABANDONED, randomDate(c.getCreatedAt(), now, random), orders, orderCounter);
        }

        // 6. recurring-payment failure
        if (orderCounter[0] < numOrders && scenarioCusts.size() > 5) {
            Customer c = scenarioCusts.get(5);
            Order o = addOrder(c, new BigDecimal("999.00"), Order.OrderStatus.FAILED, randomDate(c.getCreatedAt(), now, random), orders, orderCounter);
            if (paymentCounter[0] < numPayments) {
                OffsetDateTime pTime = randomDate(o.getCreatedAt(), now, random);
                addPayment(o, c, new BigDecimal("999.00"), Payment.PaymentStatus.FAILED, Payment.FailureReason.BANK_DECLINE, "emandate", 1, pTime, pTime.plusSeconds(5), null, payments, paymentCounter);
            }
        }

        // Fill remaining orders
        Order.OrderStatus[] oStatuses = {Order.OrderStatus.CREATED, Order.OrderStatus.ATTEMPTED, Order.OrderStatus.PAID, Order.OrderStatus.FAILED, Order.OrderStatus.ABANDONED, Order.OrderStatus.CANCELLED};
        int[] oWeights = {5, 10, 50, 20, 10, 5};
        
        while (orderCounter[0] < numOrders) {
            Customer c = customers.get(random.nextInt(customers.size()));
            Order.OrderStatus status = oStatuses[getWeightedRandom(oWeights, random)];
            BigDecimal amount = BigDecimal.valueOf(100 + (20000 - 100) * random.nextDouble());
            addOrder(c, amount, status, randomDate(c.getCreatedAt(), now, random), orders, orderCounter);
        }
        orders = orderRepository.saveAll(orders);

        // Fill remaining payments
        List<Order> validOrders = new ArrayList<>();
        for (Order o : orders) {
            if (o.getStatus() != Order.OrderStatus.ABANDONED && o.getStatus() != Order.OrderStatus.CREATED) {
                validOrders.add(o);
            }
        }
        if (validOrders.isEmpty()) validOrders.addAll(orders);

        String[] methods = {"upi", "card", "netbanking", "wallet"};
        
        while (paymentCounter[0] < numPayments) {
            Order o = validOrders.get(random.nextInt(validOrders.size()));
            Payment.PaymentStatus status;
            Payment.FailureReason reason = null;
            
            if (o.getStatus() == Order.OrderStatus.PAID) {
                status = Payment.PaymentStatus.CAPTURED;
            } else if (o.getStatus() == Order.OrderStatus.FAILED) {
                status = Payment.PaymentStatus.FAILED;
                reason = getRandomFailureReason(random);
            } else {
                Payment.PaymentStatus[] pStatuses = {Payment.PaymentStatus.CREATED, Payment.PaymentStatus.AUTHORIZED, Payment.PaymentStatus.FAILED, Payment.PaymentStatus.REFUNDED};
                status = pStatuses[random.nextInt(pStatuses.length)];
                if (status == Payment.PaymentStatus.FAILED) reason = getRandomFailureReason(random);
            }
            
            String method = methods[random.nextInt(methods.length)];
            
            int attempt = 1;
            for (Payment p : payments) {
                if (p.getOrder() != null && p.getOrder().getId().equals(o.getId())) attempt++;
            }
            
            OffsetDateTime pTime = randomDate(o.getCreatedAt(), now, random);
            OffsetDateTime failedAt = status == Payment.PaymentStatus.FAILED ? pTime.plusSeconds(1 + random.nextInt(60)) : null;
            OffsetDateTime capturedAt = status == Payment.PaymentStatus.CAPTURED ? pTime.plusSeconds(1 + random.nextInt(60)) : null;
            
            addPayment(o, o.getCustomer(), o.getAmount(), status, reason, method, attempt, pTime, failedAt, capturedAt, payments, paymentCounter);
        }
        paymentRepository.saveAll(payments);
    }

    private Order addOrder(Customer c, BigDecimal amount, Order.OrderStatus status, OffsetDateTime createdAt, List<Order> orders, int[] counter) {
        Order o = new Order();
        o.setRazorpayOrderId("order_" + UUID.randomUUID().toString().substring(0, 12));
        o.setCustomer(c);
        o.setAmount(amount);
        o.setCurrency("INR");
        o.setStatus(status);
        o.setCreatedAt(createdAt);
        o.setUpdatedAt(createdAt);
        orders.add(o);
        counter[0]++;
        return o;
    }

    private Payment addPayment(Order o, Customer c, BigDecimal amount, Payment.PaymentStatus status, Payment.FailureReason reason, String method, int attempt, OffsetDateTime createdAt, OffsetDateTime failedAt, OffsetDateTime capturedAt, List<Payment> payments, int[] counter) {
        Payment p = new Payment();
        p.setRazorpayPaymentId("pay_" + UUID.randomUUID().toString().substring(0, 12));
        p.setOrder(o);
        p.setCustomer(c);
        p.setAmount(amount);
        p.setCurrency("INR");
        p.setStatus(status);
        p.setFailureReason(reason);
        p.setMethod(method);
        p.setAttemptNumber(attempt);
        p.setCreatedAt(createdAt);
        p.setFailedAt(failedAt);
        p.setCapturedAt(capturedAt);
        p.setUpdatedAt(createdAt);
        payments.add(p);
        counter[0]++;
        return p;
    }

    private Payment.FailureReason getRandomFailureReason(Random random) {
        Payment.FailureReason[] reasons = {Payment.FailureReason.TIMEOUT, Payment.FailureReason.INSUFFICIENT_FUNDS, Payment.FailureReason.BANK_DECLINE, Payment.FailureReason.NETWORK_ERROR, Payment.FailureReason.CUSTOMER_CANCELLED, Payment.FailureReason.UNKNOWN};
        return reasons[random.nextInt(reasons.length)];
    }

    private int getWeightedRandom(int[] weights, Random random) {
        int total = 0;
        for (int w : weights) total += w;
        int r = random.nextInt(total);
        int current = 0;
        for (int i = 0; i < weights.length; i++) {
            current += weights[i];
            if (r < current) return i;
        }
        return weights.length - 1;
    }

    private OffsetDateTime randomDate(OffsetDateTime start, OffsetDateTime end, Random random) {
        long deltaSeconds = end.toEpochSecond() - start.toEpochSecond();
        long randSeconds = deltaSeconds > 0 ? (long) (random.nextDouble() * deltaSeconds) : 0;
        return start.plusSeconds(randSeconds);
    }
}
