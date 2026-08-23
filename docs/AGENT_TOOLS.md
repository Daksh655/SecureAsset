# SecureAsset — AI Agent Tools

## 1. Purpose

The SecureAsset AI Agent uses controlled application tools to investigate recovery cases and produce recovery recommendations.

The agent does not directly access:

- PostgreSQL
- Razorpay credentials
- arbitrary HTTP endpoints
- arbitrary application methods
- financial actions outside the approved tool set

All tool execution is performed by the SecureAsset backend.

---

# 2. Agent Architecture

The agent follows this model:

```text
Recovery Case
      ↓
Agent receives case context
      ↓
Agent decides what information is needed
      ↓
Agent requests an approved tool
      ↓
Backend validates the tool request
      ↓
Backend executes the tool
      ↓
Tool result returned to agent
      ↓
Agent reasons again
      ↓
Agent produces structured recommendation
      ↓
Policy validation
      ↓
Approval / execution
```
### 3. Tool Categories

Tools are divided into:

READ TOOLS
↓
Provide information to the agent.

ANALYSIS TOOLS
↓
Perform deterministic calculations.

RECOMMENDATION TOOLS
↓
Persist an AI recommendation.

ACTION TOOLS
↓
Request an external recovery action.
Action tools are more restricted than read tools.

### 4. Tool Security Rules
   The agent can only call tools explicitly registered in the SecureAsset tool registry.
   Tool arguments must be validated by the backend.
   The agent cannot provide raw SQL.
   The agent cannot provide arbitrary URLs for backend execution.
   The agent cannot access environment variables.
   The agent cannot access API secrets.
   The agent cannot bypass policy validation.
   The agent cannot directly execute a financial action without passing the required approval and policy flow.
   Every tool invocation must be recorded in the audit log.
   Tool failures must be returned as structured errors instead of crashing the agent.

### 5. Tool: getFailedPayments
Purpose
Retrieves failed payment records relevant to the current merchant context.

Category
READ
Input
{
"limit": 20,
"sinceHours": 24
}
Input Rules
limit must be between 1 and 100.
sinceHours must be between 1 and 168.
The agent cannot request unrestricted historical data.
Output
{
"payments": [
{
"paymentId": "uuid",
"orderId": "uuid",
"customerId": "uuid",
"amount": 4999.00,
"currency": "INR",
"failureReason": "TIMEOUT",
"attemptNumber": 1,
"failedAt": "timestamp"
}
],
"count": 1
}
Agent Use
The agent may use this tool when it needs to investigate recent failed payments.

### 6. Tool: getCustomerProfile
Purpose
Retrieves customer information required to understand the recovery context.

Category
READ
Input
{
"customerId": "uuid"
}
Output
{
"customerId": "uuid",
"name": "Demo Customer",
"email": "customer@example.com",
"totalOrders": 12,
"successfulPayments": 10,
"totalSpent": 48250.00
}
Agent Use
Used to determine whether the customer has a history of successful transactions and to provide context for recovery decisions.

### 7. Tool: getCustomerPaymentHistory
Purpose
Retrieves recent payment history for a specific customer.

Category
READ
Input
{
"customerId": "uuid",
"limit": 20
}
Input Rules
limit must be between 1 and 50.
Output
{
"customerId": "uuid",
"payments": [
{
"paymentId": "uuid",
"orderId": "uuid",
"amount": 2499.00,
"status": "CAPTURED",
"failureReason": null,
"attemptNumber": 1,
"createdAt": "timestamp"
}
]
}
Agent Use
Used to determine:
previous successful transactions
repeated failures
payment behavior
previous recovery opportunities

### 8. Tool: getOrderDetails
Purpose
Retrieves order information associated with a recovery case.

Category
READ
Input
{
"orderId": "uuid"
}
Output
{
"orderId": "uuid",
"customerId": "uuid",
"amount": 4999.00,
"currency": "INR",
"status": "ATTEMPTED",
"createdAt": "timestamp"
}
Agent Use
Used to understand:
order value
order state
relationship between order and payment
whether recovery is still relevant

### 9. Tool: getRelatedRecoveryCases
Purpose
Retrieves existing recovery cases associated with a customer.

Category
READ
Input
{
"customerId": "uuid",
"limit": 20
}
Output
{
"cases": [
{
"caseId": "uuid",
"problemType": "PAYMENT_FAILURE",
"riskAmount": 2499.00,
"priority": "HIGH",
"status": "FAILED",
"detectedAt": "timestamp"
}
]
}
Agent Use
Used to prevent repeated or conflicting recovery recommendations.

### 10. Tool: getRevenueRiskSummary
Purpose
Provides aggregate merchant revenue-risk information.

Category
ANALYSIS
Input
{
"sinceHours": 24
}
Output
{
"transactionsAnalyzed": 10000,
"recoveryOpportunities": 1284,
"highPriorityCases": 37,
"mediumPriorityCases": 421,
"lowPriorityCases": 826,
"revenueAtRisk": 1840000.00,
"potentiallyRecoverable": 1120000.00,
"recoveredRevenue": 470000.00,
"currency": "INR"
}
Agent Use
Useful when the merchant asks questions about overall recovery performance or when the agent needs aggregate context.

### 11. Tool: createRecoveryRecommendation
Purpose
Persists a structured AI recommendation for a Recovery Case.

Category
RECOMMENDATION
Input
{
"recoveryCaseId": "uuid",
"recommendedAction": "CREATE_PAYMENT_LINK",
"confidence": 92.0,
"reason": "Customer has a strong successful payment history and the current failure is a transient timeout.",
"evidence": [
"7 previous successful payments",
"Current failure reason is TIMEOUT",
"No previous recovery action exists"
]
}
Allowed Actions
RETRY_PAYMENT
CREATE_PAYMENT_LINK
SEND_RECOVERY_REMINDER
NO_ACTION
ESCALATE_TO_MERCHANT
Backend Rules
Confidence must be between 0 and 100.
Recommendation must reference an existing Recovery Case.
Evidence must be stored in structured form.
The recommendation does not execute the action.
The recommendation must be policy-checked before any action occurs.

### 12. Tool: createPaymentLink
Purpose
Requests creation of a Razorpay Test Mode payment link for an approved recovery.
Category
ACTION

IMPORTANT
This tool is NOT freely executable by the LLM.

The request must pass:
Agent Recommendation
↓
Policy Validation
↓
Merchant Approval when required
↓
Action Execution
Input
{
"recoveryCaseId": "uuid",
"customerId": "uuid",
"amount": 4999.00,
"currency": "INR"
}
Validation Rules

The backend must verify:
Recovery Case exists.
Recovery Case is eligible.
Recovery Case is not already recovered.
Amount matches the approved recovery amount.
Amount is greater than zero.
Amount does not exceed configured policy limits.
Duplicate recovery action does not already exist.
Required merchant approval exists.
Razorpay Test Mode is being used.
The action is associated with the correct customer/recovery case.
Output
{
"success": true,
"recoveryActionId": "uuid",
"razorpayReference": "plink_test_reference",
"paymentLink": "test-payment-link"
}
No Razorpay secret is ever returned to the agent.

### 13. Tool: recordRecoveryAction
Purpose
Records the outcome of a recovery action
Category
ACTION / RECORDING
Input
{
"recoveryCaseId": "uuid",
"actionType": "CREATE_PAYMENT_LINK",
"status": "SUCCESS",
"razorpayReference": "plink_test_reference",
"result": "Payment link created successfully"
}
Rules
Must reference an existing Recovery Case.
Must create/update a RecoveryAction.
Must write an AuditLog.
Must update the Recovery Case state where appropriate.
Must not fabricate a successful Razorpay result.

### 14. Tool Failure Format

Every tool must return a structured result.
Successful:
{
"success": true,
"data": {}
}

Failed:
{
"success": false,
"error": {
"code": "CUSTOMER_NOT_FOUND",
"message": "Customer could not be found."
}
}

The agent must receive the failure and decide whether to:
retry when appropriate
use another available tool
continue with reduced context
request merchant review
stop safely

### 15. Agent Tool Selection Rules

The agent should use the smallest set of tools required to reach a reliable decision.
Example:
Payment failure
↓
getCustomerProfile()
↓
getCustomerPaymentHistory()
↓
getOrderDetails()
↓
Reason
↓
createRecoveryRecommendation()
The agent should not call every available tool for every case.

### 16. Example Agent Investigation

Example Recovery Case:
Case:
₹7,500 payment failure
Failure reason:
TIMEOUT

Possible agent workflow:
1. getCustomerProfile()
2. getCustomerPaymentHistory()
3. getOrderDetails()
4. getRelatedRecoveryCases()
5. analyze evidence
6. createRecoveryRecommendation()
The exact tool sequence is decided by the agent based on the available context.
The backend controls what information each tool can expose.

### 17. Agent Action Boundary

The agent can recommend:
RETRY_PAYMENT
CREATE_PAYMENT_LINK
SEND_RECOVERY_REMINDER
NO_ACTION
ESCALATE_TO_MERCHANT

However:
recommendation ≠ execution
Execution always occurs through the backend.

### 18. No Direct Database Access

The agent must never execute:
SQL
or receive database credentials.

The allowed architecture is:
Agent
↓
Tool
↓
Service
↓
Repository
↓
Database

Never:
Agent
↓
Database

### 19. No Direct Razorpay Secret Access

The agent must never receive:
Razorpay API secret
LLM API key
database password
webhook secret
environment variables

The integration layer owns all external credentials.

### 20. Audit Requirements

Every tool invocation must produce an AuditLog event containing at minimum:
recoveryCaseId
eventType
actorType
toolName
success
createdAt

When safe, structured input/output information may also be recorded.
Secrets must never be written to the audit log.

### 21. Agent Failure Handling

If a required tool fails:
Tool Failure
↓
Agent receives structured error
↓
Can another tool provide sufficient evidence?
├── YES → continue
└── NO  → NEEDS_REVIEW

The agent must never invent unavailable information.
If evidence is insufficient, the preferred behavior is:
NEEDS_REVIEW
rather than a confident unsupported recommendation.

### 22. Future Tool Expansion

Additional tools may be introduced later for:
subscription history
customer communication
campaign creation
retry scheduling
recovery analytics
merchant policy configuration

Any new tool requires an explicit update to this document before implementation.

### 23. Core Principle

The SecureAsset AI Agent is not an unrestricted autonomous program.
It is a bounded reasoning system operating inside deterministic financial controls.

AI reasoning
↓
Approved tools
↓
Backend validation
↓
Policy
↓
Approval
↓
Execution
↓
Audit

The objective is not maximum autonomy.
The objective is:
useful autonomy with controlled financial execution.