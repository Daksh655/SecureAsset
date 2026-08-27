package com.secureasset.backend.controller;

import com.secureasset.backend.integration.RazorpayWebhookSignatureService;
import com.secureasset.backend.service.RazorpayWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RazorpayWebhookControllerTest {

    private RazorpayWebhookSignatureService signatureService;
    private RazorpayWebhookService webhookService;
    private RazorpayWebhookController controller;

    @BeforeEach
    void setUp() {
        signatureService = mock(RazorpayWebhookSignatureService.class);
        webhookService = mock(RazorpayWebhookService.class);
        controller = new RazorpayWebhookController(signatureService, webhookService);
    }

    @Test
    void missingSignatureRejected() {
        ResponseEntity<String> response = controller.handleWebhook("{}", null, "evt_123");
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(webhookService, never()).processWebhook(anyString(), anyString());
    }

    @Test
    void invalidSignatureRejected() {
        when(signatureService.verifySignature("{}", "bad_sig")).thenReturn(false);

        ResponseEntity<String> response = controller.handleWebhook("{}", "bad_sig", "evt_123");
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(webhookService, never()).processWebhook(anyString(), anyString());
    }

    @Test
    void validSignatureAccepted() {
        when(signatureService.verifySignature("{}", "good_sig")).thenReturn(true);

        ResponseEntity<String> response = controller.handleWebhook("{}", "good_sig", "evt_123");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(webhookService, times(1)).processWebhook("{}", "evt_123");
    }

    @Test
    void exceptionInServiceReturns500() {
        when(signatureService.verifySignature("{}", "good_sig")).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("DB Error")).when(webhookService).processWebhook("{}", "evt_123");

        ResponseEntity<String> response = controller.handleWebhook("{}", "good_sig", "evt_123");
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}
