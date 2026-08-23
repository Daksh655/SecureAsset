# SecureAsset — Database Design

## 1. Database
SecureAsset uses PostgreSQL.

Database name:
`secureasset`

Development database connection:
```text
Host: localhost
Port: 5433
Database: secureasset
Username: secureasset
```
The PostgreSQL container exposes host port 5433 and internally uses PostgreSQL port 5432.

### 2. Database Design Principles

The database must:

Keep financial facts separate from AI decisions.
Keep recovery actions separate from recovery cases.
Maintain a complete audit trail.
Use deterministic identifiers and timestamps.
Store monetary values using NUMERIC, never floating-point types.
Preserve external Razorpay identifiers.
Support large synthetic datasets efficiently.
Allow the AI agent to operate through application services instead of direct database access.

### 3. Core Tables

SecureAsset uses six primary tables:

customers
orders
payments
recovery_cases
recovery_actions
audit_logs

Relationship overview:
Customer
│
├────────< Orders
│              │
│              └────────< Payments
│
└────────< RecoveryCases

Order
└────────< RecoveryCases

Payment
└────────< RecoveryCases

RecoveryCase
├────────< RecoveryActions
└────────< AuditLogs

### 4. customers

Stores merchant customer information needed for recovery analysis.

Fields
| Field                | Type                     | Null | Default           | Description                                 |
| -------------------- | ------------------------ | ---: | ----------------- | ------------------------------------------- |
| id                   | UUID                     |   NO | generated         | Internal customer identifier                |
| razorpay_customer_id | VARCHAR(100)             |  YES | NULL              | Razorpay customer identifier when available |
| name                 | VARCHAR(150)             |   NO | —                 | Customer name                               |
| email                | VARCHAR(255)             |  YES | NULL              | Customer email                              |
| phone                | VARCHAR(30)              |  YES | NULL              | Customer phone                              |
| created_at           | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Record creation time                        |
| updated_at           | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Last update time                            |


Constraints
id is the primary key.
razorpay_customer_id, when present, should be unique.
Email is not required to be unique because synthetic/demo data may contain controlled duplicates.
Indexes
PRIMARY KEY (id)
UNIQUE INDEX (razorpay_customer_id)
INDEX (email)

### 5. orders

Stores merchant orders associated with customers.

Fields
| Field             | Type                     | Null | Default           | Description                 |
| ----------------- | ------------------------ | ---: | ----------------- | --------------------------- |
| id                | UUID                     |   NO | generated         | Internal order identifier   |
| razorpay_order_id | VARCHAR(100)             |  YES | NULL              | Razorpay order identifier   |
| customer_id       | UUID                     |   NO | —                 | Customer who owns the order |
| amount            | NUMERIC(14,2)            |   NO | —                 | Order amount                |
| currency          | CHAR(3)                  |   NO | `INR`             | ISO currency code           |
| status            | VARCHAR(30)              |   NO | —                 | Internal order status       |
| created_at        | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Order creation time         |
| updated_at        | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Last update time            |

Allowed order statuses
CREATED
ATTEMPTED
PAID
FAILED
ABANDONED
CANCELLED
Constraints
id is the primary key.
customer_id references customers.id.
razorpay_order_id, when present, should be unique.
amount >= 0.
currency defaults to INR.
Indexes
PRIMARY KEY (id)
UNIQUE INDEX (razorpay_order_id)
INDEX (customer_id)
INDEX (status)
INDEX (created_at)

### 6. payments

Stores individual payment attempts.
A single order can have multiple payment attempts.

Fields
| Field               | Type                     | Null | Default           | Description                      |
| ------------------- | ------------------------ | ---: | ----------------- | -------------------------------- |
| id                  | UUID                     |   NO | generated         | Internal payment identifier      |
| razorpay_payment_id | VARCHAR(100)             |  YES | NULL              | Razorpay payment identifier      |
| order_id            | UUID                     |   NO | —                 | Associated order                 |
| customer_id         | UUID                     |   NO | —                 | Customer associated with payment |
| amount              | NUMERIC(14,2)            |   NO | —                 | Payment amount                   |
| currency            | CHAR(3)                  |   NO | `INR`             | ISO currency code                |
| status              | VARCHAR(30)              |   NO | —                 | Payment status                   |
| failure_reason      | VARCHAR(255)             |  YES | NULL              | Normalized failure reason        |
| method              | VARCHAR(50)              |  YES | NULL              | Payment method                   |
| attempt_number      | INTEGER                  |   NO | `1`               | Attempt number for the order     |
| created_at          | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Payment creation time            |
| failed_at           | TIMESTAMP WITH TIME ZONE |  YES | NULL              | Failure timestamp                |
| captured_at         | TIMESTAMP WITH TIME ZONE |  YES | NULL              | Capture timestamp                |
| updated_at          | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Last update time                 |

Allowed payment statuses
CREATED
AUTHORIZED
CAPTURED
FAILED
REFUNDED
Allowed normalized failure reasons
TIMEOUT
INSUFFICIENT_FUNDS
BANK_DECLINE
NETWORK_ERROR
CUSTOMER_CANCELLED
UNKNOWN

The original Razorpay failure information can be preserved in the integration/audit layer when needed. The database uses the normalized category for recovery analysis.

Constraints
id is the primary key.
order_id references orders.id.
customer_id references customers.id.
razorpay_payment_id, when present, should be unique.
amount >= 0.
attempt_number >= 1.
failed_at should be populated for failed payments.
captured_at should be populated for captured payments.
Indexes
PRIMARY KEY (id)
UNIQUE INDEX (razorpay_payment_id)
INDEX (order_id)
INDEX (customer_id)
INDEX (status)
INDEX (failure_reason)
INDEX (created_at)
INDEX (status, created_at)

The composite (status, created_at) index supports efficient retrieval of recent failed payments.

### 7. recovery_cases

Represents a revenue-recovery opportunity rather than a raw payment event.
This is the main operational object used by SecureAsset.
A failed payment does NOT automatically become a recovery case.
The Revenue Risk Engine determines whether an event is eligible and creates a recovery case only when appropriate.

Fields
| Field                | Type                     | Null | Default           | Description                             |
| -------------------- | ------------------------ | ---: | ----------------- | --------------------------------------- |
| id                   | UUID                     |   NO | generated         | Recovery case identifier                |
| customer_id          | UUID                     |   NO | —                 | Customer involved                       |
| order_id             | UUID                     |  YES | NULL              | Related order when applicable           |
| payment_id           | UUID                     |  YES | NULL              | Related payment when applicable         |
| problem_type         | VARCHAR(40)              |   NO | —                 | Type of revenue-loss problem            |
| risk_amount          | NUMERIC(14,2)            |   NO | —                 | Revenue currently considered at risk    |
| recovery_score       | INTEGER                  |   NO | `0`               | Deterministic recovery score from 0–100 |
| priority             | VARCHAR(20)              |   NO | —                 | Case priority                           |
| eligibility          | VARCHAR(20)              |   NO | —                 | Whether recovery is eligible            |
| status               | VARCHAR(30)              |   NO | `NEW`             | Recovery case lifecycle state           |
| agent_status         | VARCHAR(30)              |   NO | `NOT_ANALYZED`    | AI analysis state                       |
| agent_recommendation | VARCHAR(50)              |  YES | NULL              | Recommended recovery action             |
| agent_confidence     | NUMERIC(5,2)             |  YES | NULL              | AI confidence from 0–100                |
| agent_reason         | TEXT                     |  YES | NULL              | Human-readable explanation              |
| detected_at          | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Time the opportunity was detected       |
| analyzed_at          | TIMESTAMP WITH TIME ZONE |  YES | NULL              | Time AI analysis completed              |
| resolved_at          | TIMESTAMP WITH TIME ZONE |  YES | NULL              | Time case reached terminal state        |
| updated_at           | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Last update                             |

Allowed problem types
PAYMENT_FAILURE
REPEATED_PAYMENT_FAILURE
CHECKOUT_ABANDONMENT
RECURRING_PAYMENT_FAILURE
Allowed priorities
HIGH
MEDIUM
LOW
Allowed eligibility values
ELIGIBLE
INELIGIBLE

Only eligible cases are normally promoted into active recovery workflows.

Allowed recovery case statuses
NEW
ANALYZING
ACTION_REQUIRED
PENDING_APPROVAL
EXECUTING
RECOVERED
FAILED
DISMISSED
EXPIRED
Allowed agent statuses
NOT_ANALYZED
ANALYZING
ANALYZED
FAILED
NEEDS_REVIEW
Allowed agent recommendations
RETRY_PAYMENT
CREATE_PAYMENT_LINK
SEND_RECOVERY_REMINDER
NO_ACTION
ESCALATE_TO_MERCHANT
Constraints
id is the primary key.
customer_id references customers.id.
order_id, when present, references orders.id.
payment_id, when present, references payments.id.
risk_amount >= 0.
recovery_score must be between 0 and 100.
agent_confidence, when present, must be between 0 and 100.
A recovery case may exist without a payment because checkout abandonment can occur before a successful payment record exists.
A recovery case must reference at least a customer and one relevant revenue context such as order or payment.
Important uniqueness rule

SecureAsset must prevent duplicate active recovery cases for the same underlying revenue-loss event.
Application-level logic must ensure that the same payment/order situation is not independently recovered multiple times.

Indexes
PRIMARY KEY (id)
INDEX (customer_id)
INDEX (order_id)
INDEX (payment_id)
INDEX (priority)
INDEX (status)
INDEX (problem_type)
INDEX (recovery_score)
INDEX (detected_at)
INDEX (priority, status, recovery_score)

The composite (priority, status, recovery_score) index supports the main merchant recovery queue.

### 8. recovery_actions

Represents an attempted or completed recovery action.
A recovery case may have multiple actions over its lifetime, but duplicate financial actions must be prevented by application-level policy.

Fields
| Field              | Type                     | Null | Default           | Description                         |
| ------------------ | ------------------------ | ---: | ----------------- | ----------------------------------- |
| id                 | UUID                     |   NO | generated         | Action identifier                   |
| recovery_case_id   | UUID                     |   NO | —                 | Related recovery case               |
| action_type        | VARCHAR(50)              |   NO | —                 | Type of recovery action             |
| amount             | NUMERIC(14,2)            |  YES | NULL              | Financial amount involved           |
| status             | VARCHAR(30)              |   NO | `PENDING`         | Action execution status             |
| approval_status    | VARCHAR(30)              |   NO | `NOT_REQUIRED`    | Approval state                      |
| razorpay_reference | VARCHAR(150)             |  YES | NULL              | Razorpay resource/action identifier |
| result             | TEXT                     |  YES | NULL              | Successful result                   |
| error_code         | VARCHAR(100)             |  YES | NULL              | External/internal error code        |
| error_message      | TEXT                     |  YES | NULL              | Failure information                 |
| requested_at       | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Action request time                 |
| approved_at        | TIMESTAMP WITH TIME ZONE |  YES | NULL              | Approval time                       |
| executed_at        | TIMESTAMP WITH TIME ZONE |  YES | NULL              | Execution time                      |
| completed_at       | TIMESTAMP WITH TIME ZONE |  YES | NULL              | Completion time                     |

Allowed action types
RETRY_PAYMENT
CREATE_PAYMENT_LINK
SEND_RECOVERY_REMINDER
NO_ACTION
ESCALATE
Allowed action statuses
PENDING
APPROVED
REJECTED
EXECUTING
SUCCESS
FAILED
BLOCKED
CANCELLED
Allowed approval statuses
NOT_REQUIRED
PENDING
APPROVED
REJECTED
Constraints
id is the primary key.
recovery_case_id references recovery_cases.id.
amount, when present, must be >= 0.
Financial actions must pass policy validation before execution.
The LLM cannot directly create an executable financial action.
Indexes
PRIMARY KEY (id)
INDEX (recovery_case_id)
INDEX (action_type)
INDEX (status)
INDEX (requested_at)
INDEX (razorpay_reference)

### 9. audit_logs

Stores the complete event history needed to explain how a recovery decision was made and what happened during execution.
Audit logs are append-oriented records.
Existing audit records should not be modified as part of normal application operation.

Fields
| Field              | Type                     | Null | Default           | Description                      |
| ------------------ | ------------------------ | ---: | ----------------- | -------------------------------- |
| id                 | UUID                     |   NO | generated         | Audit event identifier           |
| recovery_case_id   | UUID                     |  YES | NULL              | Related recovery case            |
| recovery_action_id | UUID                     |  YES | NULL              | Related action                   |
| event_type         | VARCHAR(50)              |   NO | —                 | Type of audit event              |
| actor_type         | VARCHAR(30)              |   NO | —                 | Who/what produced the event      |
| tool_name          | VARCHAR(100)             |  YES | NULL              | Agent tool involved              |
| input_data         | JSONB                    |  YES | NULL              | Structured input                 |
| output_data        | JSONB                    |  YES | NULL              | Structured output                |
| message            | TEXT                     |  YES | NULL              | Human-readable event explanation |
| success            | BOOLEAN                  |   NO | —                 | Whether the event succeeded      |
| created_at         | TIMESTAMP WITH TIME ZONE |   NO | current timestamp | Event time                       |


Allowed actor types
SYSTEM
AGENT
MERCHANT
RAZORPAY
Example event types
CASE_CREATED
CASE_ANALYSIS_STARTED
TOOL_CALLED
TOOL_FAILED
AGENT_RECOMMENDATION_CREATED
POLICY_CHECKED
ACTION_APPROVAL_REQUESTED
ACTION_APPROVED
ACTION_REJECTED
ACTION_BLOCKED
RAZORPAY_REQUEST
RAZORPAY_RESPONSE
RECOVERY_SUCCEEDED
RECOVERY_FAILED
CASE_DISMISSED
CASE_EXPIRED
Constraints
id is the primary key.
recovery_case_id, when present, references recovery_cases.id.
recovery_action_id, when present, references recovery_actions.id.
Audit records should be append-only.
Secrets, API keys, passwords, and sensitive credentials must never be stored in audit data.
Indexes
PRIMARY KEY (id)
INDEX (recovery_case_id)
INDEX (recovery_action_id)
INDEX (event_type)
INDEX (actor_type)
INDEX (created_at)

### 10. Money Representation

All monetary values are stored using:
NUMERIC(14,2)

Never use:
FLOAT
DOUBLE
REAL

for financial amounts.
Amounts represent currency units, not the smallest currency sub-unit.

Example:
₹2499.00

Currency is stored separately using an ISO 4217 three-character code.
The initial supported currency is: INR

### 11. Identifiers

SecureAsset uses UUIDs as internal primary keys.
External Razorpay identifiers are stored separately.

Example:
Internal:
8b0b2d8e-...
External:
pay_RAZORPAY123

This prevents external provider identifiers from becoming the internal database primary key.

### 12. Timestamp Convention

All timestamps are stored as:
TIMESTAMP WITH TIME ZONE
The backend should persist timestamps in UTC.
The frontend converts timestamps to the merchant's display timezone when necessary.

### 13. Database Access Rules

The AI agent must never directly access PostgreSQL.
The agent accesses information through approved application tools.

AI Agent
↓
Agent Tool
↓
Application Service
↓
Repository
↓
PostgreSQL

This keeps the agent's capabilities bounded.

### 14. Data Lifecycle

Raw transaction data:
Razorpay / Synthetic Data
↓
Customer / Order / Payment
↓
Revenue Risk Engine
↓
Recovery Case
↓
Recovery Action
↓
Audit Log

Financial facts must not be overwritten simply because the AI reaches a different conclusion.
AI decisions are stored separately from the underlying payment/order facts.

### 15. Initial Database Scope

SecureAsset intentionally does not introduce separate tables for:

merchants
products
notifications
campaigns
subscriptions
AI conversations
agent memory
model telemetry
users/authentication

unless a later requirement explicitly requires them.

---

### `RecoveryCase` is not a payment
This is critical.

A payment:
```text
₹4,999 → FAILED

is a fact.

A recovery case:

₹4,999 → potentially recoverable → HIGH priority
```
is a decision/opportunity.

That separation is what lets SecureAsset analyze thousands of payments without showing thousands of rows.
Checkout abandonment can have no payment
That's why payment_id is nullable.

Example:
Customer
   ↓
Order created
   ↓
Checkout started
   ↓
No payment

This can still become a RecoveryCase.
Audit is separate

We need to be able to demonstrate:
What did the agent see?
What tool did it call?
What did it recommend?
Why?
What policy check happened?
Who approved it?
What did Razorpay return?

That's why audit_logs exists.