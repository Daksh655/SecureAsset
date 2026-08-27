package com.secureasset.backend.controller;

import com.secureasset.backend.integration.RazorpayWebhookSignatureService;
import com.secureasset.backend.service.RazorpayWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/razorpay")
public class RazorpayWebhookController {

    private final RazorpayWebhookSignatureService signatureService;
    private final RazorpayWebhookService webhookService;

    public RazorpayWebhookController(RazorpayWebhookSignatureService signatureService, RazorpayWebhookService webhookService) {
        this.signatureService = signatureService;
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {

        if (signature == null || signature.isEmpty()) {
            return ResponseEntity.badRequest().body("Missing signature");
        }

        if (!signatureService.verifySignature(rawPayload, signature)) {
            return ResponseEntity.status(401).body("Invalid signature");
        }

        try {
            webhookService.processWebhook(rawPayload, eventId);
            return ResponseEntity.ok("Success");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing webhook");
        }
    }
}
