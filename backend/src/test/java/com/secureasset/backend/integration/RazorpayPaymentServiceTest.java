package com.secureasset.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.secureasset.backend.agent.tools.dto.RazorpayPaymentStatusResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RazorpayPaymentServiceTest {

    private RestTemplate restTemplate;
    private RazorpayPaymentService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new RazorpayPaymentService(restTemplate);
        objectMapper = new ObjectMapper();
    }

    @Test
    void successfulResponseIsNormalized() {
        ObjectNode mockNode = objectMapper.createObjectNode();
        mockNode.put("id", "pay_12345");
        mockNode.put("status", "captured");
        mockNode.put("amount", 50000); // 500.00
        mockNode.put("currency", "INR");
        mockNode.put("method", "card");
        mockNode.put("captured", true);
        mockNode.put("created_at", 1600000000L);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(mockNode, HttpStatus.OK));

        UUID orderId = UUID.randomUUID();
        Optional<RazorpayPaymentStatusResult> resultOpt = service.fetchPaymentDetails("pay_12345", orderId);

        assertThat(resultOpt).isPresent();
        RazorpayPaymentStatusResult result = resultOpt.get();
        assertThat(result.paymentId()).isEqualTo("pay_12345");
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("captured");
        assertThat(result.amount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.method()).isEqualTo("card");
        assertThat(result.captured()).isTrue();
        assertThat(result.failureReason()).isNull();
        assertThat(result.failureCode()).isNull();
    }

    @Test
    void failedPaymentResponseIsNormalized() {
        ObjectNode mockNode = objectMapper.createObjectNode();
        mockNode.put("id", "pay_failed");
        mockNode.put("status", "failed");
        mockNode.put("error_code", "BAD_REQUEST_ERROR");
        mockNode.put("error_description", "Payment failed");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(mockNode, HttpStatus.OK));

        Optional<RazorpayPaymentStatusResult> resultOpt = service.fetchPaymentDetails("pay_failed", null);

        assertThat(resultOpt).isPresent();
        RazorpayPaymentStatusResult result = resultOpt.get();
        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.failureCode()).isEqualTo("BAD_REQUEST_ERROR");
        assertThat(result.failureReason()).isEqualTo("Payment failed");
    }

    @Test
    void externalApiErrorReturnsEmpty() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        Optional<RazorpayPaymentStatusResult> resultOpt = service.fetchPaymentDetails("pay_err", null);
        assertThat(resultOpt).isEmpty();
    }
    
    @Test
    void restClientExceptionReturnsEmptySafely() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new RestClientException("Connection timed out"));

        Optional<RazorpayPaymentStatusResult> resultOpt = service.fetchPaymentDetails("pay_timeout", null);
        assertThat(resultOpt).isEmpty();
    }
}
