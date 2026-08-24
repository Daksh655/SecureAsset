package com.secureasset.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    public enum ActorType {
        SYSTEM,
        AGENT,
        MERCHANT,
        RAZORPAY
    }

    public enum EventType {
        CASE_CREATED,
        CASE_ANALYSIS_STARTED,
        TOOL_CALLED,
        TOOL_FAILED,
        AGENT_RECOMMENDATION_CREATED,
        POLICY_CHECKED,
        ACTION_APPROVAL_REQUESTED,
        ACTION_APPROVED,
        ACTION_REJECTED,
        ACTION_BLOCKED,
        RAZORPAY_REQUEST,
        RAZORPAY_RESPONSE,
        RECOVERY_SUCCEEDED,
        RECOVERY_FAILED,
        CASE_DISMISSED,
        CASE_EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_case_id")
    private RecoveryCase recoveryCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_action_id")
    private RecoveryAction recoveryAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30)
    private ActorType actorType;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "input_data", columnDefinition = "JSONB")
    private String inputData;

    @Column(name = "output_data", columnDefinition = "JSONB")
    private String outputData;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public AuditLog() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RecoveryCase getRecoveryCase() {
        return recoveryCase;
    }

    public void setRecoveryCase(RecoveryCase recoveryCase) {
        this.recoveryCase = recoveryCase;
    }

    public RecoveryAction getRecoveryAction() {
        return recoveryAction;
    }

    public void setRecoveryAction(RecoveryAction recoveryAction) {
        this.recoveryAction = recoveryAction;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public void setActorType(ActorType actorType) {
        this.actorType = actorType;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getInputData() {
        return inputData;
    }

    public void setInputData(String inputData) {
        this.inputData = inputData;
    }

    public String getOutputData() {
        return outputData;
    }

    public void setOutputData(String outputData) {
        this.outputData = outputData;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}