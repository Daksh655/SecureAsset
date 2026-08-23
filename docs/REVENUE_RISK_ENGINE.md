# SecureAsset — Revenue Risk Engine

## 1. Purpose

The Revenue Risk Engine is the deterministic decision layer responsible for converting raw payment/order/revenue events into prioritized recovery opportunities.

Its purpose is to answer:

1. Is this event potentially recoverable?
2. How much revenue is at risk?
3. How likely is recovery to be worthwhile?
4. How urgent is the opportunity?
5. Should a Recovery Case be created?
6. What priority should the case receive?

The Revenue Risk Engine operates before the AI Agent.

The AI Agent does not process all raw financial events.

---

# 2. Core Principle

A raw financial event is not automatically a recovery opportunity.

The engine evaluates each event using deterministic rules.

```text
Raw Event
    ↓
Normalize
    ↓
Eligibility
    ↓
Recoverability
    ↓
Scoring
    ↓
Priority
    ↓
Deduplication
    ↓
Recovery Case
```
### 3. Input Events

The engine initially evaluates:
Payment Events
FAILED
CAPTURED
AUTHORIZED
REFUNDED
Order Events
CREATED
ATTEMPTED
PAID
ABANDONED
CANCELLED
Recurring Payment Events
Synthetic recurring-payment scenarios may be represented using payment records and recovery metadata.

### 4. Revenue-Loss Candidate Types

The engine initially identifies four candidate types.
4.1 PAYMENT_FAILURE
A payment associated with an otherwise valid order has failed.
Conditions:
payment.status = FAILED
and:
order exists
customer exists
and:
no successful/captured payment already resolves the same order
4.2 REPEATED_PAYMENT_FAILURE
A customer/order has experienced multiple unsuccessful payment attempts.
Initial condition:
failed_attempt_count >= 2
The engine checks whether a prior recovery action has already been attempted.
Repeated failures do not automatically justify repeated recovery actions.
4.3 CHECKOUT_ABANDONMENT
A customer has an order/checkout opportunity but no completed payment occurred within the configured abandonment window.
Initial demo window:
30 minutes
A future implementation may make this merchant-configurable.
4.4 RECURRING_PAYMENT_FAILURE
A recurring/subscription payment fails and represents potential future revenue loss.
The MVP uses synthetic recurring-payment scenarios because the core goal is to demonstrate the recovery workflow rather than implement a complete subscription billing platform.

### 5. Eligibility Rules

A candidate must pass all applicable eligibility checks.
General Rules
A candidate is not eligible when:
customer does not exist
or:
revenue has already been successfully recovered
or:
the same recovery event already has an active recovery case
or:
the case has already reached a terminal state
or:
the transaction is explicitly excluded by policy

### 6. Duplicate Prevention
SecureAsset must prevent repeated recovery workflows for the same underlying event.
Examples:
Payment
A payment should not create multiple active recovery cases.

Order
An abandoned order should not repeatedly create cases every time the detector runs.

Customer
A customer with multiple independent failed payments may have multiple cases, but each case must correspond to a distinct revenue event.
Deduplication is enforced using application logic and database constraints/indexes where appropriate.

### 7. Recovery Score

Every eligible recovery candidate receives a deterministic score from:
0 to 100
The score represents the relative value and recoverability of the opportunity.
It is NOT an AI confidence score.
The score is calculated before the AI Agent runs.

### 8. Scoring Factors

The initial score uses five components:
Amount Value
Customer History
Failure Recoverability
Recency
Recovery History

Total:
Recovery Score =
Amount Score
+ Customer History Score
+ Recoverability Score
+ Recency Score
+ Recovery History Score

Maximum:
100

### 9. Amount Value Score

Higher-value recoverable revenue receives a higher amount component.
Initial scoring:
| Amount          | Score |
| --------------- | ----: |
| < ₹500          |     5 |
| ₹500 – ₹1,999   |    10 |
| ₹2,000 – ₹4,999 |    15 |
| ₹5,000 – ₹9,999 |    18 |
| >= ₹10,000      |    20 |

Maximum:
20
This is not intended to represent the probability of success.
It represents the financial impact of the opportunity.

### 10. Customer History Score

Customer history provides evidence of whether the customer has previously completed successful transactions.
Initial scoring:
Customer History	Score
No previous successful payment	3
1–2 successful payments	7
3–5 successful payments	10
6–9 successful payments	14
>= 10 successful payments	18
Maximum:
18

### 11. Failure Recoverability Score

Different failure types have different initial recoverability assumptions.
Failure Type	Score
TIMEOUT	25
NETWORK_ERROR	25
UNKNOWN	15
CUSTOMER_CANCELLED	8
BANK_DECLINE	5
INSUFFICIENT_FUNDS	4

Maximum:
25
These values represent the MVP's initial deterministic heuristic and can later be tuned using observed recovery outcomes.

### 12. Recency Score

More recent opportunities receive higher urgency.
Time Since Event	Score
< 15 minutes	17
15–60 minutes	12
1–6   hours	    8
6–24  hours	    5
>24   hours	    2

Maximum:
17

### 13. Recovery History Score

Previous recovery attempts reduce the priority of additional automatic interventions.

Previous Recovery Attempts	Score
0	20
1	10
2	3
>2	0

Maximum:
20
However, this score alone does not determine eligibility.
Policy may completely prevent additional actions even when a case has a positive score.

### 14. Score Interpretation

Initial priority ranges:
80–100 → HIGH
50–79  → MEDIUM
1–49   → LOW
0      → INELIGIBLE
The ranges are configurable in code/configuration and may be tuned during evaluation.

### 15. Recovery Eligibility

A candidate can receive a positive score but still be ineligible.
Examples:
Already recovered
Payment failed
↓
Later payment captured
↓
INELIGIBLE
Duplicate active case
Payment already has active RecoveryCase
↓
INELIGIBLE
Recovery limit exceeded
Previous recovery attempts >= configured maximum
↓
INELIGIBLE
Invalid financial state
Amount <= 0
↓
INELIGIBLE
Eligibility always overrides score.

### 16. Example Scoring

Consider:
Customer:
12 previous successful payments

Payment:
₹7,500

Failure:
TIMEOUT

Event age:
10 minutes

Previous recovery attempts:
0

Scores:
Amount = 18
Customer History = 18
Recoverability = 25
Recency = 17
Recovery History = 20

Total:
98

Result:
Eligibility = ELIGIBLE
Priority = HIGH
Recovery Score = 98
Risk Amount = ₹7,500

This becomes a Recovery Case candidate.

### 17. Low-Value Example
Customer:
New customer

Payment:
₹299

Failure:
INSUFFICIENT_FUNDS

Age:
20 hours

Previous recovery attempts:
2

Scores:
Amount = 5
Customer History = 3
Recoverability = 4
Recency = 5
Recovery History = 3

Total:
20
Result:
Priority = LOW
The merchant does not need to manually inspect this case.

### 18. Recovery Case Creation

When an eligible candidate passes the engine:
Candidate
↓
RecoveryCase created

The RecoveryCase stores:
customer
order
payment
problem type
risk amount
recovery score
priority
eligibility
detection timestamp

The AI Agent can then analyze the case.

### 19. Merchant Queue Strategy

The merchant dashboard should not display every transaction.
The default queue should prioritize:
HIGH priority
↓
MEDIUM priority
↓
LOW priority

Default page size:
20 cases

The merchant can filter by:
priority
problem type
amount
status
recovery score
date

### 20. Aggregate Dashboard

The dashboard should provide aggregate metrics rather than raw transaction volume.
Core metrics:
Total Revenue at Risk
Potentially Recoverable Revenue
Recovered Revenue
Recovery Opportunities
High Priority Cases
Auto-Recovery Eligible Cases
Recovery Rate

Example:
Transactions analyzed:
10,000

Recovery opportunities:
1,284

High priority:
37

Medium priority:
421

Low priority:
826

Revenue at risk:
₹18,40,000

Potentially recoverable:
₹11,20,000

Recovered:
₹4,70,000
The values above are illustrative and will be calculated from the actual dataset.

### 21. Large Dataset Processing

The system must not send every raw event to the LLM.
For example:
50,000 payment records
↓
Revenue Risk Engine
↓
5,000 candidates
↓
1,284 eligible opportunities
↓
37 HIGH
↓
AI Agent focuses on selected cases

The exact numbers depend on the dataset.
The important architectural principle is:
deterministic filtering first
AI reasoning second

### 22. AI Boundary

The Revenue Risk Engine determines:
whether a case should exist
how much revenue is at risk
priority
eligibility

The AI Agent determines:
what contextual information to investigate
what recovery intervention appears appropriate
how to explain the recommendation

The AI must not override:
eligibility
financial limits
duplicate prevention
approval policy

### 23. Recovery Actions

The engine does not execute recovery actions.
Its output is:
RecoveryCase

The next layer is responsible for:

AI recommendation
↓
Policy
↓
Approval
↓
Execution

### 24. Failure Handling

The Revenue Risk Engine must fail safely.
If scoring fails:
No recovery action should execute.

If customer history is unavailable:
The case may be marked NEEDS_REVIEW.

If duplicate detection cannot be completed:
Do not automatically execute a recovery action.
Financial operations should fail closed rather than fail open.

### 25. Evaluation Metrics

SecureAsset should measure the Revenue Risk Engine using:
Detection volume
Candidates detected
Eligible rate
Eligible Candidates / Candidates Detected
Recovery value
Sum of risk_amount for eligible cases
Recovery rate
Recovered Revenue / Potentially Recoverable Revenue
False opportunity rate

Cases classified as recoverable but later determined to be non-actionable.
Duplicate prevention
Number of duplicate recovery actions prevented.

### 26. Future Improvements

The MVP uses deterministic heuristics.
Future versions could use learned models to optimize:
recovery probability
intervention selection
customer segmentation
optimal timing
expected recovered value

However, model-based scoring is outside the initial MVP.

### 27. Core Principle

SecureAsset does not attempt to recover everything.
It attempts to identify:
the right revenue, from the right customers, at the right time, with the right intervention.
The system should optimize for useful recovery opportunities rather than maximum case volume.