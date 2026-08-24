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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recovery_cases")
public class RecoveryCase {

    public enum ProblemType {
        PAYMENT_FAILURE,
        REPEATED_PAYMENT_FAILURE,
        CHECKOUT_ABANDONMENT,
        RECURRING_PAYMENT_FAILURE
    }

    public enum Priority {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum Eligibility {
        ELIGIBLE,
        INELIGIBLE
    }

    public enum Status {
        NEW,
        ANALYZING,
        ACTION_REQUIRED,
        PENDING_APPROVAL,
        EXECUTING,
        RECOVERED,
        FAILED,
        DISMISSED,
        EXPIRED
    }

    public enum AgentStatus {
        NOT_ANALYZED,
        ANALYZING,
        ANALYZED,
        FAILED,
        NEEDS_REVIEW
    }

    public enum AgentRecommendation {
        RETRY_PAYMENT,
        CREATE_PAYMENT_LINK,
        SEND_RECOVERY_REMINDER,
        NO_ACTION,
        ESCALATE_TO_MERCHANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "problem_type", nullable = false, length = 40)
    private ProblemType problemType;

    @Column(name = "risk_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal riskAmount;

    @Column(name = "recovery_score", nullable = false)
    private Integer recoveryScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Eligibility eligibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_status", nullable = false, length = 30)
    private AgentStatus agentStatus = AgentStatus.NOT_ANALYZED;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_recommendation", length = 50)
    private AgentRecommendation agentRecommendation;

    @Column(name = "agent_confidence", precision = 5, scale = 2)
    private BigDecimal agentConfidence;

    @Column(name = "agent_reason", columnDefinition = "TEXT")
    private String agentReason;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public RecoveryCase() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public ProblemType getProblemType() {
        return problemType;
    }

    public void setProblemType(ProblemType problemType) {
        this.problemType = problemType;
    }

    public BigDecimal getRiskAmount() {
        return riskAmount;
    }

    public void setRiskAmount(BigDecimal riskAmount) {
        this.riskAmount = riskAmount;
    }

    public Integer getRecoveryScore() {
        return recoveryScore;
    }

    public void setRecoveryScore(Integer recoveryScore) {
        this.recoveryScore = recoveryScore;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Eligibility getEligibility() {
        return eligibility;
    }

    public void setEligibility(Eligibility eligibility) {
        this.eligibility = eligibility;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public AgentStatus getAgentStatus() {
        return agentStatus;
    }

    public void setAgentStatus(AgentStatus agentStatus) {
        this.agentStatus = agentStatus;
    }

    public AgentRecommendation getAgentRecommendation() {
        return agentRecommendation;
    }

    public void setAgentRecommendation(AgentRecommendation agentRecommendation) {
        this.agentRecommendation = agentRecommendation;
    }

    public BigDecimal getAgentConfidence() {
        return agentConfidence;
    }

    public void setAgentConfidence(BigDecimal agentConfidence) {
        this.agentConfidence = agentConfidence;
    }

    public String getAgentReason() {
        return agentReason;
    }

    public void setAgentReason(String agentReason) {
        this.agentReason = agentReason;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(OffsetDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    public OffsetDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(OffsetDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}