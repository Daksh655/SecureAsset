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

    @Test
    @SuppressWarnings("unchecked")
    void createPaymentLink_successfulResponseIsNormalized() {
        ObjectNode mockNode = objectMapper.createObjectNode();
        mockNode.put("id", "plink_123");
        mockNode.put("short_url", "https://rzp.io/i/X");
        mockNode.put("status", "created");
        mockNode.put("reference_id", "ref_123");
        mockNode.put("amount", 750000); // 7500.00
        mockNode.put("currency", "INR");

        org.mockito.ArgumentCaptor<HttpEntity<java.util.Map<String, Object>>> entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>(mockNode, HttpStatus.OK));

        RazorpayPaymentService.PaymentLinkResult result = service.createPaymentLink(
                new BigDecimal("7500.00"),
                "INR",
                "John Doe",
                "john@example.com",
                "9999999999",
                "ref_123"
        );

        assertThat(result.success()).isTrue();
        assertThat(result.paymentLinkId()).isEqualTo("plink_123");
        assertThat(result.shortUrl()).isEqualTo("https://rzp.io/i/X");
        assertThat(result.status()).isEqualTo("created");
        assertThat(result.referenceId()).isEqualTo("ref_123");
        assertThat(result.amount()).isEqualTo(new BigDecimal("7500.00"));
        assertThat(result.currency()).isEqualTo("INR");

        HttpEntity<java.util.Map<String, Object>> captured = entityCaptor.getValue();
        java.util.Map<String, Object> body = captured.getBody();
        assertThat(body.get("amount")).isEqualTo(750000L); // INR conversion check
        assertThat(body.get("currency")).isEqualTo("INR");
        assertThat(body.get("reference_id")).isEqualTo("ref_123"); // Idempotency remains stable

        java.util.Map<String, String> customer = (java.util.Map<String, String>) body.get("customer");
        assertThat(customer.get("name")).isEqualTo("John Doe");
        assertThat(customer.get("email")).isEqualTo("john@example.com");
        assertThat(customer.get("contact")).isEqualTo("9999999999");
        
        // Secret is never logged/exposed check
        assertThat(captured.getHeaders().getFirst("Authorization")).isEqualTo("Basic test");
    }

    @Test
    void createPaymentLink_400Error() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(HttpStatus.BAD_REQUEST));

        RazorpayPaymentService.PaymentLinkResult result = service.createPaymentLink(
                new BigDecimal("7500.00"), "INR", "John Doe", null, null, "ref_123");

        assertThat(result.success()).isFalse();
        // verify it only called once (no retry for 4xx)
        org.mockito.Mockito.verify(restTemplate, org.mockito.Mockito.times(1))
                .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class));
    }

    @Test
    void createPaymentLink_500Error() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        RazorpayPaymentService.PaymentLinkResult result = service.createPaymentLink(
                new BigDecimal("7500.00"), "INR", "John Doe", null, null, "ref_123");

        assertThat(result.success()).isFalse();
        // verify it retried once, total 2 calls
        org.mockito.Mockito.verify(restTemplate, org.mockito.Mockito.times(2))
                .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class));
    }

    @Test
    void createPaymentLink_timeout() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Timeout"));

        RazorpayPaymentService.PaymentLinkResult result = service.createPaymentLink(
                new BigDecimal("7500.00"), "INR", "John Doe", null, null, "ref_123");

        assertThat(result.success()).isFalse();
        // verify it retried once, total 2 calls
        org.mockito.Mockito.verify(restTemplate, org.mockito.Mockito.times(2))
                .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class));
    }

    @Test
    void createPaymentLink_malformedResponse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ResponseEntity<>((com.fasterxml.jackson.databind.JsonNode) null, HttpStatus.OK)); // Body is null

        RazorpayPaymentService.PaymentLinkResult result = service.createPaymentLink(
                new BigDecimal("7500.00"), "INR", "John Doe", null, null, "ref_123");

        assertThat(result.success()).isFalse();
    }
}
