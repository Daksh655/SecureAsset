package com.secureasset.backend;

import com.secureasset.backend.entity.RecoveryAction;
import com.secureasset.backend.repository.RecoveryActionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class DebugWebhookAuditTest {

    @Autowired
    private RecoveryActionRepository actionRepo;

    @Test
    public void printAuditLogs() {
        System.out.println("=== DIAGNOSTIC RECOVERY ACTIONS START ===");
        List<RecoveryAction> actions = actionRepo.findAll();
        for (RecoveryAction a : actions) {
            System.out.println("Action ID: " + a.getId() + " | Type: " + a.getActionType() + " | Status: " + a.getStatus() + " | Ref: " + a.getRazorpayReference());
        }
        System.out.println("=== DIAGNOSTIC RECOVERY ACTIONS END ===");
    }
}
