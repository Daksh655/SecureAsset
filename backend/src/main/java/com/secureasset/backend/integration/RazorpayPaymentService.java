package com.secureasset.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.secureasset.backend.agent.tools.dto.RazorpayPaymentStatusResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class RazorpayPaymentService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentService.class);
    private static final String API_BASE_URL = "https://api.razorpay.com/v1";

    private final RestTemplate restTemplate;
    private final String authHeader;

    @org.springframework.beans.factory.annotation.Autowired
    public RazorpayPaymentService(
            @Value("${razorpay.api.key:default_key}") String apiKey,
            @Value("${razorpay.api.secret:default_secret}") String apiSecret) {
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
                
        String auth = apiKey + ":" + apiSecret;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }
    
    // For unit testing
    public RazorpayPaymentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.authHeader = "Basic test";
    }

    public Optional<RazorpayPaymentStatusResult> fetchPaymentDetails(String paymentId, UUID internalOrderId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            String url = API_BASE_URL + "/payments/" + paymentId;
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(normalize(response.getBody(), internalOrderId));
            } else {
                log.warn("Razorpay API returned unexpected status: {}", response.getStatusCode());
                return Optional.empty();
            }
        } catch (RestClientException e) {
            log.error("Failed to fetch payment details from Razorpay for paymentId: {}", paymentId, e);
            return Optional.empty();
        }
    }
    
    private RazorpayPaymentStatusResult normalize(JsonNode node, UUID internalOrderId) {
        String paymentId = node.has("id") ? node.get("id").asText() : null;
        String status = node.has("status") ? node.get("status").asText() : null;
        
        BigDecimal amount = null;
        if (node.has("amount")) {
            // Razorpay amounts are in subunits (paise for INR)
            amount = new BigDecimal(node.get("amount").asText()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        
        String currency = node.has("currency") ? node.get("currency").asText() : null;
        String method = node.has("method") ? node.get("method").asText() : null;
        boolean captured = node.has("captured") && node.get("captured").asBoolean();
        
        OffsetDateTime createdAt = null;
        if (node.has("created_at")) {
            long epochSeconds = node.get("created_at").asLong();
            createdAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("UTC"));
        }
        
        String failureReason = (node.has("error_description") && !node.get("error_description").isNull()) 
                ? node.get("error_description").asText() : null;
        String failureCode = (node.has("error_code") && !node.get("error_code").isNull()) 
                ? node.get("error_code").asText() : null;

        return new RazorpayPaymentStatusResult(
                paymentId,
                internalOrderId,
                status,
                amount,
                currency,
                method,
                captured,
                createdAt,
                failureReason,
                failureCode,
                OffsetDateTime.now()
        );
    }

    public record PaymentLinkResult(
            boolean success,
            String paymentLinkId,
            String shortUrl,
            String referenceId,
            String status,
            BigDecimal amount,
            String currency
    ) {
        public static PaymentLinkResult failure() {
            return new PaymentLinkResult(false, null, null, null, null, null, null);
        }
    }

    public PaymentLinkResult createPaymentLink(
            BigDecimal amount,
            String currency,
            String customerName,
            String customerEmail,
            String customerContact,
            String referenceId
    ) {
        java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
        long amountInSubunits = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValue();
        requestBody.put("amount", amountInSubunits);
        requestBody.put("currency", currency != null ? currency : "INR");
        requestBody.put("reference_id", referenceId);

        java.util.Map<String, String> customerNode = new java.util.HashMap<>();
        if (customerName != null) customerNode.put("name", customerName);
        if (customerEmail != null) customerNode.put("email", customerEmail);
        if (customerContact != null) customerNode.put("contact", customerContact);
        requestBody.put("customer", customerNode);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.set("Content-Type", "application/json");
        HttpEntity<java.util.Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        String url = API_BASE_URL + "/payment_links";

        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode node = response.getBody();
                    
                    String id = node.has("id") ? node.get("id").asText() : null;
                    String shortUrl = node.has("short_url") ? node.get("short_url").asText() : null;
                    String status = node.has("status") ? node.get("status").asText() : null;
                    String refId = node.has("reference_id") ? node.get("reference_id").asText() : null;
                    String curr = node.has("currency") ? node.get("currency").asText() : null;
                    BigDecimal resultAmount = null;
                    if (node.has("amount")) {
                        resultAmount = new BigDecimal(node.get("amount").asText()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    }
                    
                    return new PaymentLinkResult(true, id, shortUrl, refId, status, resultAmount, curr);
                } else {
                    log.warn("Razorpay API returned unexpected status: {}", response.getStatusCode());
                    return PaymentLinkResult.failure();
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                log.error("Razorpay 4xx error while creating payment link: {}", e.getStatusCode());
                return PaymentLinkResult.failure();
            } catch (org.springframework.web.client.HttpServerErrorException | org.springframework.web.client.ResourceAccessException e) {
                if (attempt == maxAttempts) {
                    log.error("Razorpay transient error, max retries reached: {}", e.getMessage());
                    return PaymentLinkResult.failure();
                }
                log.warn("Razorpay transient error, retrying: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error calling Razorpay: {}", e.getMessage());
                return PaymentLinkResult.failure();
            }
        }
        return PaymentLinkResult.failure();
    }
}
