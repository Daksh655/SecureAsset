# SecureAsset — API Specification

## 1. Purpose

This document defines the public REST API exposed by the SecureAsset Spring Boot backend.

The API is used by:

- React frontend
- Razorpay webhook integration
- Internal application components through services

The AI Agent does not directly expose public HTTP endpoints.

---

# 2. API Base URL

Development:

```text
http://localhost:8080/api
```
All application endpoints use:
/api

### 3. API Design Principles
   REST-style resource naming.
   JSON request and response bodies.
   HTTP status codes must represent the result accurately.
   Financial operations must be idempotent where applicable.
   Error responses use a consistent structure.
   The frontend never communicates directly with Razorpay using secret credentials.
   AI agent execution occurs inside backend services.
   Pagination is required for potentially large collections.
   Public API responses must not expose secrets or internal credentials.

### 4. Standard Error Response

All API errors should use this structure:
{
"timestamp": "2026-08-24T10:30:00Z",
"status": 400,
"error": "BAD_REQUEST",
"message": "Invalid recovery case request.",
"path": "/api/recovery-cases/123"
}

For validation errors:
{
"timestamp": "2026-08-24T10:30:00Z",
"status": 400,
"error": "VALIDATION_ERROR",
"message": "Request validation failed.",
"path": "/api/recovery-cases/123",
"fieldErrors": {
"amount": "Amount must be greater than zero."
}
}

### 5. Dashboard API
GET /api/dashboard

Returns merchant-level recovery metrics.

Query Parameters
sinceHours
Optional.

Default:
24
Example
GET /api/dashboard?sinceHours=24
Response
{
"transactionsAnalyzed": 10000,
"recoveryOpportunities": 1284,
"highPriorityCases": 37,
"mediumPriorityCases": 421,
"lowPriorityCases": 826,
"revenueAtRisk": 1840000.00,
"potentiallyRecoverable": 1120000.00,
"recoveredRevenue": 470000.00,
"recoveryRate": 41.96,
"currency": "INR"
}
### 6. Recovery Case APIs
GET /api/recovery-cases

Returns a paginated list of recovery opportunities.
Query Parameters
page
size
priority
status
problemType
minAmount
maxAmount
minScore

Defaults:
page = 0
size = 20

Maximum:
size = 100
Example
GET /api/recovery-cases?page=0&size=20&priority=HIGH
Response
{
"content": [
{
"id": "uuid",
"customerId": "uuid",
"orderId": "uuid",
"paymentId": "uuid",
"problemType": "PAYMENT_FAILURE",
"riskAmount": 7500.00,
"recoveryScore": 98,
"priority": "HIGH",
"status": "NEW",
"agentStatus": "NOT_ANALYZED",
"detectedAt": "2026-08-24T10:30:00Z"
}
],
"page": 0,
"size": 20,
"totalElements": 37,
"totalPages": 2
}
GET /api/recovery-cases/{id}

Returns complete information for a recovery case.
Response
{
"id": "uuid",
"customer": {
"id": "uuid",
"name": "Demo Customer",
"email": "customer@example.com"
},
"order": {
"id": "uuid",
"amount": 7500.00,
"currency": "INR",
"status": "ATTEMPTED"
},
"payment": {
"id": "uuid",
"amount": 7500.00,
"status": "FAILED",
"failureReason": "TIMEOUT",
"attemptNumber": 1
},
"problemType": "PAYMENT_FAILURE",
"riskAmount": 7500.00,
"recoveryScore": 98,
"priority": "HIGH",
"status": "NEW",
"agentStatus": "NOT_ANALYZED",
"agentRecommendation": null,
"agentConfidence": null,
"agentReason": null,
"detectedAt": "2026-08-24T10:30:00Z",
"analyzedAt": null
}

### 7. Analyze Recovery Case
POST /api/recovery-cases/{id}/analyze

Starts AI investigation for the recovery case.
Request
No request body required.
Example
POST /api/recovery-cases/{id}/analyze
Processing
Recovery Case
↓
Build Agent Context
↓
Agent Investigation
↓
Tool Calls
↓
Diagnosis
↓
Recommendation
↓
Policy Pre-check
↓
Persist Recommendation
Response
{
"recoveryCaseId": "uuid",
"agentStatus": "ANALYZED",
"recommendation": {
"action": "CREATE_PAYMENT_LINK",
"confidence": 92.0,
"reason": "Customer has a strong successful payment history and the current payment failed due to a transient timeout.",
"evidence": [
"7 previous successful payments",
"Current failure reason is TIMEOUT",
"No previous recovery action exists"
]
}
}
Rules
Case must exist.
Case must be eligible.
Case must not already be recovered.
A case cannot be analyzed concurrently more than once.
Agent failures must produce a safe error state.
The endpoint does not execute a financial action.

### 8. Approve Recovery Action
POST /api/recovery-cases/{id}/approve

Approves a previously generated recovery recommendation.
Request
{
"actionType": "CREATE_PAYMENT_LINK",
"amount": 7500.00
}
Validation

The backend must verify:
Recovery case exists.
Recovery case is eligible.
Recommendation exists.
Requested action matches the approved recommendation.
Amount matches the approved amount.
Policy limits are satisfied.
The case is not already recovered.
No conflicting recovery action exists.
Response
{
"recoveryCaseId": "uuid",
"recoveryActionId": "uuid",
"approvalStatus": "APPROVED",
"status": "EXECUTING"
}
Approval does not mean that Razorpay has already completed the action.

### 9. Reject Recovery Action
POST /api/recovery-cases/{id}/reject

Rejects the current recovery recommendation.
Request
{
"reason": "Merchant does not want to contact this customer."
}
Response
{
"recoveryCaseId": "uuid",
"status": "DISMISSED",
"approvalStatus": "REJECTED"
}

### 10. Recovery Action Result
GET /api/recovery-cases/{id}/actions

Returns recovery actions associated with the case.
Response
{
"actions": [
{
"id": "uuid",
"actionType": "CREATE_PAYMENT_LINK",
"amount": 7500.00,
"status": "SUCCESS",
"approvalStatus": "APPROVED",
"razorpayReference": "plink_test_reference",
"result": "Payment link created successfully.",
"requestedAt": "2026-08-24T10:30:00Z",
"executedAt": "2026-08-24T10:31:00Z"
}
]
}

### 11. Audit API
GET /api/recovery-cases/{id}/audit

Returns the audit trail for a recovery case.
Response
{
"events": [
{
"id": "uuid",
"eventType": "CASE_CREATED",
"actorType": "SYSTEM",
"toolName": null,
"message": "Recovery case created.",
"success": true,
"createdAt": "2026-08-24T10:00:00Z"
},
{
"id": "uuid",
"eventType": "TOOL_CALLED",
"actorType": "AGENT",
"toolName": "getCustomerPaymentHistory",
"message": "Customer payment history retrieved.",
"success": true,
"createdAt": "2026-08-24T10:01:00Z"
},
{
"id": "uuid",
"eventType": "AGENT_RECOMMENDATION_CREATED",
"actorType": "AGENT",
"toolName": "createRecoveryRecommendation",
"message": "Payment link recommended.",
"success": true,
"createdAt": "2026-08-24T10:02:00Z"
},
{
"id": "uuid",
"eventType": "ACTION_APPROVED",
"actorType": "MERCHANT",
"toolName": null,
"message": "Merchant approved recovery action.",
"success": true,
"createdAt": "2026-08-24T10:03:00Z"
}
]
}

### 12. Razorpay Webhook API
POST /api/webhooks/razorpay

Receives Razorpay webhook events.
Headers
The integration must validate the Razorpay webhook signature before processing the payload.

Important header:
X-Razorpay-Signature
Supported Event Categories

Initial implementation should support relevant payment/order events such as:
payment.failed
payment.authorized
payment.captured
order.paid

Additional events can be added later.

Processing Flow
Razorpay Webhook
↓
Signature Verification
↓
Event Validation
↓
Idempotency Check
↓
Normalize Event
↓
Persist / Update Data
↓
Trigger Revenue Risk Evaluation
Response

Successful processing:
HTTP 200

Invalid signature:
HTTP 401

Invalid payload:
HTTP 400

Internal failure:
HTTP 500

### 13. Health API
GET /api/health

Returns application health.
Response
{
"status": "UP"
}
This endpoint is for development/deployment monitoring.

### 14. Data Generation API

The synthetic dataset generator is primarily a development script and is not a production merchant API.
Therefore no public dataset-generation endpoint is required.
Synthetic data should be generated using scripts or controlled development utilities.

### 15. Pagination

Any endpoint returning potentially large collections must support pagination.
Default:
page = 0
size = 20

Maximum:
size = 100
Large raw transaction datasets must never be returned in a single API response.

### 16. Filtering

Recovery-case filtering must support:
priority
status
problemType
minAmount
maxAmount
minScore

Additional filters may be added without changing the core architecture.

### 17. Idempotency

Financial action endpoints must prevent duplicate execution.
For example:
POST approve
↓
Action created
↓
same request repeated
↓
existing action returned
The backend must not create multiple equivalent financial actions because the client retries a request.

### 18. State Transitions

Recovery Case lifecycle:
NEW
↓
ANALYZING
↓
ACTION_REQUIRED
↓
PENDING_APPROVAL
↓
EXECUTING
↓
RECOVERED

Alternative terminal paths:
NEW → DISMISSED
NEW → EXPIRED

ANALYZING → FAILED
ACTION_REQUIRED → DISMISSED

PENDING_APPROVAL → DISMISSED
EXECUTING → FAILED
Invalid state transitions must be rejected.

### 19. Public API vs Internal Agent Operations

Public API:
React
↓
Spring Boot Controller
↓
Service

Agent operation:
Agent
↓
Tool Registry
↓
Agent Tool
↓
Service
↓
Repository / External Integration
Agent tools do not become public HTTP endpoints merely because the agent can use them.

### 20. Security Boundaries

The following must never be sent to the React frontend:
Razorpay API secret
Razorpay webhook secret
LLM API key
database password
environment variables
internal credentials

Razorpay API calls must occur from the backend.

### 21. API Versioning

Versioning is not required for the initial buildathon MVP.
The initial API remains:
/api/...
If the system later becomes a production service with multiple consumers, API versioning can be introduced.

### 22. API Design Principle

The API should expose the merchant's business workflow rather than raw database operations.
Preferred:
GET /api/recovery-cases
POST /api/recovery-cases/{id}/analyze
POST /api/recovery-cases/{id}/approve

Avoid exposing generic CRUD endpoints such as:
POST /api/payments
PUT /api/payments/{id}
DELETE /api/recovery-case/{id}