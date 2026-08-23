
---

# 2. `ARCHITECTURE.md`

We'll next lock the actual system architecture.

Use:

```md
# SecureAsset — Architecture

## 1. Architecture Style

SecureAsset uses a modular monolithic architecture.

The initial implementation is one Spring Boot backend with clearly separated application modules. The frontend is a separate React application.

The system intentionally avoids microservices because the MVP is a two-week build and does not require distributed infrastructure.

---

## 2. High-Level Architecture

```text
                         ┌──────────────────────┐
                         │    React Frontend    │
                         │    Merchant UI       │
                         └──────────┬───────────┘
                                    │
                                  REST
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │   Spring Boot API    │
                         └──────────┬───────────┘
                                    │
        ┌───────────────────────────┼──────────────────────────┐
        │                           │                          │
        ▼                           ▼                          ▼
┌──────────────────┐     ┌───────────────────┐      ┌──────────────────┐
│ Revenue Risk     │     │ Recovery Case     │      │ AI Agent         │
│ Engine           │     │ Service           │      │ Service          │
└────────┬─────────┘     └─────────┬─────────┘      └────────┬─────────┘
         │                         │                          │
         └─────────────────────────┼──────────────────────────┘
                                   │
                                   ▼
                          ┌───────────────────┐
                          │    PostgreSQL     │
                          └─────────┬─────────┘
                                    │
                                    ▼
                          ┌───────────────────┐
                          │ Policy /          │
                          │ Guardrail Layer   │
                          └─────────┬─────────┘
                                    │
                              Approval Gate
                                    │
                                    ▼
                          ┌───────────────────┐
                          │ Razorpay Test API │
                          └─────────┬─────────┘
                                    │
                                    ▼
                              Action Result
                                    │
                                    ▼
                              Audit Logging

```

### 3. Major Components
##   3.1 React Frontend

Responsibilities:

merchant dashboard
recovery opportunity list
recovery case details
recommendation display
approval/rejection
audit display
recovery metrics

The frontend never receives Razorpay secrets or LLM secrets.

## 3.2 Spring Boot API

Responsibilities:

REST API
application orchestration
validation
business rules
persistence
integration coordination

## 3.3 Revenue Risk Engine

Responsibilities:

identify candidate revenue-loss events
evaluate eligibility
determine recoverability
calculate recovery score
assign priority
estimate revenue at risk
create or update Recovery Cases

This component is deterministic.

The LLM is not used for initial transaction filtering.

## 3.4 Recovery Case Service

Responsibilities:

create recovery cases
maintain case state
retrieve case information
update case outcomes
prevent duplicate recovery workflows
## 3.5 AI Agent Service

Responsibilities:

investigate recovery cases
gather context using approved tools
reason over structured context
diagnose likely cause
recommend an appropriate intervention
provide structured explanation

The agent cannot directly access PostgreSQL or arbitrary APIs.

## 3.6 Policy / Guardrail Layer

Responsibilities:

validate proposed financial actions
enforce action limits
prevent duplicate recovery
enforce approval requirements
reject invalid or unsafe actions

All financial actions must pass through this layer.

## 3.7 Razorpay Integration Layer

Responsibilities:

communicate with Razorpay Test APIs
create/read supported payment resources
create payment links when approved
receive and process Razorpay webhooks
map external API results into internal models

Secrets are backend-only.

## 3.8 Audit Layer

Records:

recovery case
agent recommendation
reason
tools called
tool results
policy decision
approval decision
Razorpay action
action result
failure information
timestamps


### 4. AI vs Deterministic Logic
   Deterministic
   transaction validation
   eligibility
   scoring
   priority
   amount calculations
   financial limits
   duplicate prevention
   authorization
   API execution
   persistence
   audit
   AI
   contextual investigation
   diagnosis
   recovery-strategy selection
   explanation
   tool selection among approved tools

### 5. Agent Execution Flow

Recovery Case
↓
Build Case Context
↓
Send context + approved tools to LLM
↓
LLM requests information
↓
Backend executes requested tool
↓
Tool result returned to agent
↓
Agent reasons again
↓
Agent produces structured recommendation
↓
Policy validation
↓
Approval if required
↓
Execution
↓
Outcome
↓
Audit

### 6. Financial Safety Principle

The LLM is never the final authority for a financial action.

The architecture is:
LLM recommendation
↓
Deterministic policy validation
↓
Merchant approval if required
↓
Razorpay API execution

### 7. Scalability Strategy

The architecture should be able to process large transaction datasets without sending all data to the LLM.

Large dataset
↓
Deterministic filtering
↓
Recovery candidates
↓
Prioritization
↓
Selected Recovery Cases
↓
AI investigation

This reduces unnecessary LLM calls and keeps financial filtering deterministic.