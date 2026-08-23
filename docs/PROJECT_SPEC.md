# SecureAsset — Project Specification

## 1. Product Overview

SecureAsset is an AI-powered merchant revenue recovery platform.

The system analyzes merchant payment, order, and customer activity to identify revenue that is potentially being lost. It converts raw financial events into prioritized recovery opportunities, uses an AI agent to investigate those opportunities and recommend bounded recovery actions, executes approved actions through Razorpay Test APIs, and records the complete decision and execution history.

SecureAsset is designed as a proof-of-work project for the Razorpay Buildathon Track 03 — AI Revenue Recovery.

---

## 2. Core Problem

Merchants can lose revenue through events such as:

- failed payments
- repeated payment failures
- checkout abandonment
- recurring/subscription payment failures

A merchant may have thousands of such events.

Showing all raw events to a merchant is not useful.

SecureAsset therefore acts as a decision layer between raw transaction data and merchant action.

It answers:

1. Which events represent potentially recoverable revenue?
2. Which opportunities are worth prioritizing?
3. Why is the revenue at risk?
4. What recovery intervention is appropriate?
5. Is the proposed action allowed by policy?
6. Should the action require merchant approval?
7. What happened after the action?
8. How much revenue was actually recovered?

---

## 3. Product Objective

The core objective is to complete the following loop:

detect
→ prioritize
→ investigate
→ diagnose
→ recommend
→ validate
→ approve
→ execute
→ observe
→ audit
→ measure

---

## 4. Supported Recovery Scenarios

SecureAsset initially supports four revenue-loss scenarios:

### 4.1 Failed Payment

A payment attempt fails and may be recoverable through an appropriate intervention.

### 4.2 Repeated Payment Failure

A customer experiences multiple failed attempts and the system evaluates whether another recovery intervention is appropriate.

### 4.3 Checkout Abandonment

A customer begins a checkout/payment flow but does not complete the transaction.

### 4.4 Recurring / Subscription Payment Failure

A recurring payment fails and creates potential revenue loss.

---

## 5. Core Capabilities

SecureAsset provides:

- merchant revenue-risk dashboard
- revenue-at-risk calculation
- recovery opportunity detection
- eligibility filtering
- recovery scoring
- recovery prioritization
- recovery case creation
- AI-powered case investigation
- AI diagnosis
- AI recovery recommendations
- controlled agent tool calling
- financial-action guardrails
- merchant approval workflow
- Razorpay Test API integration
- recovery action execution
- action outcome tracking
- audit trail
- recovery performance metrics

---

## 6. Data Sources

SecureAsset uses two types of data.

### 6.1 Razorpay Test Mode

Razorpay Test Mode is used for:

- API integration
- order/payment integration
- payment-link execution
- webhook/event integration
- demonstrating the real payment-system boundary

No real money or production customer data is used.

### 6.2 Synthetic Data

Synthetic data is used to:

- create large datasets
- reproduce realistic merchant scenarios
- test recovery prioritization
- test agent behavior
- test edge cases
- evaluate system performance
- create deterministic demonstration scenarios

Synthetic data must never be represented as real customer information.

---

## 7. Core Product Flow

```text

Raw transaction/event
        ↓
Transaction storage
        ↓
Revenue Risk Engine
        ↓
Eligibility filtering
        ↓
Recoverability scoring
        ↓
Recovery Case
        ↓
AI Agent investigation
        ↓
Diagnosis
        ↓
Recovery recommendation
        ↓
Policy / Guardrail validation
        ↓
Merchant approval when required
        ↓
Razorpay Test API action
        ↓
Result
        ↓
Audit log
        ↓
Recovery metrics 
```

### 8. AI Responsibilities

The AI agent is responsible for:

investigating recovery cases
gathering relevant context using approved tools
interpreting customer/payment/order history
diagnosing potential causes
selecting an appropriate recovery strategy
explaining the recommendation
producing structured recommendations
deciding which approved information-gathering tool to call

The AI agent is NOT responsible for:

directly accessing the database
directly accessing secrets
bypassing business rules
directly executing unrestricted financial actions
deciding policy limits
overriding deterministic validations


### 9. Deterministic Responsibilities

The backend is responsible for:

data validation
eligibility rules
recovery scoring
priority classification
financial calculations
duplicate-action prevention
policy enforcement
approval enforcement
API execution
audit logging
state transitions
security boundaries


### 10. Recovery Opportunity Model

A failed or incomplete payment does not automatically become a recovery case.

The Revenue Risk Engine evaluates each event using deterministic rules and produces:

eligibility
recoverability
recovery score
priority
estimated revenue at risk

Only meaningful opportunities are promoted to Recovery Cases.


### 11. Merchant Experience

The merchant should primarily see:

total revenue at risk
potentially recoverable revenue
recovered revenue
number of recovery opportunities
high-priority cases
auto-recovery eligible cases
recovery rate
important recovery recommendations

The merchant should NOT be forced to manually inspect thousands of raw transactions.


### 12. MVP Definition

The MVP is considered complete when SecureAsset can successfully demonstrate:

ingestion/storage of payment and order data
detection of revenue-risk candidates
creation of prioritized recovery cases
AI investigation of a recovery case
AI-generated recovery recommendation
policy/guardrail validation
merchant approval for gated actions
execution through Razorpay Test APIs
failure handling
audit trail
recovery metrics


### 13. Out of Scope

The following are outside the current MVP:

production payment processing
real-money transactions
real customer data
mobile application
Kubernetes
microservices
complex distributed infrastructure
LLM fine-tuning
vector database / RAG
dedicated ML fraud model
full production identity platform
enterprise multi-tenant infrastructure
unrestricted autonomous financial transactions


### 14. Primary Success Criterion

SecureAsset must demonstrate that AI is used meaningfully inside a reliable financial workflow.

The project should demonstrate:

real problem understanding
useful AI reasoning
strong backend engineering
controlled financial actions
explainability
failure handling
measurable recovery value