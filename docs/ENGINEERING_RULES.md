# SecureAsset — Engineering Rules

## 1. Purpose

This document defines the engineering rules for implementing SecureAsset.

These rules apply to:

- human developers
- AI coding assistants
- code-generation tools
- automated refactoring tools

The goal is to keep the implementation aligned with the locked architecture and prevent unnecessary complexity.

---

# 2. Source of Truth

The following documents define the current SecureAsset architecture:

```text
PROJECT_SPEC.md
ARCHITECTURE.md
DATABASE.md
REVENUE_RISK_ENGINE.md
AGENT_TOOLS.md
API_SPEC.md
ENGINEERING_RULES.md
```
These documents take precedence over assumptions made by a coding assistant.

If an implementation request conflicts with these documents, the conflict must be reported before implementation.

3. Architecture Rules
   SecureAsset uses a modular monolithic Spring Boot backend.
   React is the frontend application.
   PostgreSQL is the primary database.
   Razorpay Test APIs are the external payment integration.
   The AI Agent operates through controlled backend tools.
   Financial actions pass through deterministic policy validation.
   Recovery actions are auditable.
   Microservices are not part of the MVP.
   Do not introduce distributed infrastructure without explicit approval.

4. Dependency Rules

Do not add a new dependency merely because it is commonly used.
A new dependency requires a clear technical reason.
Do not introduce:
Kafka
Redis
RabbitMQ
Kubernetes
Vector databases
RAG frameworks
LLM agent frameworks
Spring Cloud
Graph databases

unless an explicit project requirement is added and the architecture documentation is updated first.
The initial implementation should remain intentionally lightweight.

5. Backend Package Structure

The backend should use the following logical package structure:
com.secureasset.backend
│
├── controller
├── service
├── repository
├── entity
├── dto
├── agent
├── integration
├── config
└── exception

Additional packages may be introduced only when justified by a concrete requirement.
Do not create packages simply because they are common in tutorials.

6. Entity Rules

The initial database model contains six core entities:
Customer
Order
Payment
RecoveryCase
RecoveryAction
AuditLog

Do not create additional persistent entities unless:
a genuine requirement exists,
the database design is updated,
the architecture remains consistent.

Do not duplicate entities for different API views.

7. DTO Rules

Use DTOs at API boundaries.
Do not expose JPA entities directly from public controllers unless there is an explicit reason.
Use DTOs for:
API requests
API responses
agent tool inputs
agent tool outputs

Do not create multiple DTOs that represent the same contract.
Reuse an existing DTO when its purpose is already satisfied.

8. Controller Rules

Controllers should contain:
request mapping
request validation
delegation to services
response creation

Controllers must not contain:
database queries
complex business logic
financial decision logic
LLM reasoning
Razorpay integration logic
Example:

Controller
↓
Service
↓
Repository / Integration

9. Service Rules

Business logic belongs in services.
Examples:
RevenueRiskService
RecoveryCaseService
RecoveryActionService
AgentService
RazorpayService
AuditService

Services should remain focused.
Do not create services with unrelated responsibilities merely to reduce the number of files.

10. Repository Rules

Repositories are responsible for database access.
Repositories must not:
call the LLM
call Razorpay APIs
execute financial decisions
contain agent logic
Use repositories through application services.

11. Financial Logic Rules

Financial calculations must be deterministic.
The LLM must never be treated as the source of truth for:
transaction amount
recovery amount
eligibility
policy limits
duplicate prevention
financial authorization

Monetary values must use BigDecimal in Java.
Never use:
float
double

for financial values.

12. AI Agent Rules

The AI Agent is a bounded reasoning component.
The agent may:
investigate recovery cases
call approved information tools
interpret structured context
diagnose potential causes
recommend recovery actions
explain recommendations

The agent may not:
execute arbitrary SQL
access environment variables
access secrets
make arbitrary HTTP requests
bypass backend validation
override policy limits
directly execute unrestricted financial operations

13. Tool Rules

Every AI tool must:
Exist in AGENT_TOOLS.md.
Have a defined input schema.
Have a defined output schema.
Validate its arguments.
Handle failures safely.
Produce an audit event.
Use application services rather than direct database access.
Do not create an agent tool simply to avoid writing normal backend business logic.

14. AI Decision Rules

Use deterministic code where deterministic code is appropriate.
Use AI when contextual reasoning is valuable.

Prefer deterministic logic for:
eligibility
scoring
amount calculations
policy validation
duplicate prevention
state transitions
financial validation
Prefer AI for:
investigation
diagnosis
context interpretation
intervention selection
explanation
Do not add AI merely for demonstration purposes.

15. LLM Output Rules

LLM responses must be converted into structured application data before being used by business logic.
Never parse financial decisions from free-form prose when structured output is possible.
For recommendations, require structured fields such as:

action
confidence
reason
evidence
Backend validation must run after the LLM produces the recommendation.

16. Financial Action Rules

Financial actions follow:
AI Recommendation
↓
Backend Validation
↓
Policy Check
↓
Merchant Approval when required
↓
Execution
↓
Result
↓
Audit

No step may be skipped.
The LLM recommendation is never equivalent to authorization.

17. Razorpay Integration Rules

Razorpay secrets must exist only in backend configuration.
Never place:
RAZORPAY_KEY_SECRET
RAZORPAY_WEBHOOK_SECRET

in the React frontend.
Never commit credentials to Git.
Use environment variables or deployment secrets.
Razorpay communication must occur through the integration layer.
Controllers and agents must not directly contain Razorpay API calls.

18. Webhook Rules

Razorpay webhook requests must:
Validate the webhook signature.
Validate the payload.
Apply idempotency handling.
Normalize the event.
Update application state.
Trigger appropriate revenue-risk processing.
Record the event when appropriate.
A webhook must never blindly mutate financial state.

19. Error Handling

Use consistent exception handling.
The backend must return structured API errors.
Do not expose:
stack traces
database credentials
environment variables
API secrets
internal security details
External API failures must be translated into safe application errors.

20. Failure-Safe Principle

SecureAsset should fail closed for financial actions.
If the system cannot verify:
eligibility
amount
policy
approval
duplicate state
external API validity

then the financial action must not execute automatically.
Prefer:
NEEDS_REVIEW

over unsafe execution.

21. Recovery Case State Rules

Recovery Case transitions must follow API_SPEC.md.
Do not directly modify state from arbitrary controllers.
State transitions must be validated by the service layer.
Invalid transitions must be rejected.

22. Audit Rules

Every important recovery operation must produce an audit event.
At minimum record:
case
event
actor
tool
success/failure
timestamp

Where appropriate also record:
reason
input
output
external reference
Never store secrets in audit logs.
Audit records should be treated as append-only.

23. Database Rules

Use PostgreSQL.

Use:
UUID
BigDecimal
Instant / OffsetDateTime

for appropriate Java representations.
Use database constraints and indexes defined in DATABASE.md.
Do not add indexes without a demonstrated query/use case.
Do not perform destructive schema changes without explicit approval.

24. API Rules

Every public API must:
exist in API_SPEC.md
have a clear purpose
use validation
return structured responses
use appropriate HTTP status codes
Do not expose generic CRUD endpoints merely because the underlying entity exists.
The API should represent the business workflow.

25. Frontend Rules

The frontend must:
communicate through the backend API
never hold backend secrets
display meaningful loading states
display meaningful error states
avoid exposing raw internal exceptions
paginate large datasets
present recovery opportunities rather than thousands of raw transactions
The dashboard should prioritize actionable information.

26. Data Rules

Synthetic data must be clearly separated from Razorpay Test Mode data.
Synthetic data may be used for:
volume
evaluation
deterministic test scenarios
demonstration
It must not be represented as real merchant/customer data.

27. No Unnecessary Abstraction

Do not create:
generic base classes without a concrete need
interfaces with only one implementation without a reason
factories for one object
wrappers around simple methods
utility classes for one-time logic
excessive inheritance
Prefer simple, readable code.

28. No Scope Creep

Do not add features such as:
authentication
user management
notifications
campaigns
billing
subscription management
multi-tenancy
admin panels

unless the feature is explicitly required by the current implementation plan.
A feature can be considered after the core recovery workflow is complete.

29. AI Coding Assistant Rules

Before modifying code, an AI coding assistant must:
Read the relevant documentation.
Inspect the existing implementation.
Identify files that actually need modification.
Reuse existing code when appropriate.
State the intended change when requested.
Implement only the requested scope.
Run relevant tests.
Avoid unrelated refactoring.
The AI coding assistant must not redesign SecureAsset automatically.

30. File Creation Rules

Before creating a new file, determine whether an existing file can reasonably support the requirement.
Create a new file only when it has a distinct responsibility.

Do not create:
CustomerService2
CustomerUtils
CustomerHelper
PaymentServiceNew
PaymentManager
GenericManager

simply because an existing class already exists.

31. Documentation Rules

If an architectural decision changes:

Update the relevant document.
Review affected documents.
Only then implement the change.

Code and architecture documentation should not intentionally diverge.

32. Testing Rules

Every meaningful business feature should have tests for:
normal behavior
invalid input
expected failure
important edge cases
Financial logic must be tested independently of the LLM.
Agent behavior should be tested using controlled scenarios.

33. Git Rules

Make focused commits.
Examples:
feat: add customer and order persistence
feat: implement revenue risk scoring
feat: add recovery case workflow
feat: add agent tool registry
feat: integrate razorpay test api
feat: add recovery guardrails

docs: define database schema
docs: define agent tools
Avoid giant commits containing unrelated changes.

Never commit:
.env
API keys
secrets
passwords
private credentials

34. AI-Assisted Development Principle

AI coding tools are implementation assistants, not architecture owners.
The developer remains responsible for:
architecture
security
business rules
correctness
testing
understanding the generated code
Generated code must be reviewed before being accepted.

35. Definition of Done

A feature is complete only when:
implementation is working
relevant tests pass
API behavior matches the specification
database behavior matches the schema
errors are handled
audit requirements are satisfied
no unnecessary files or dependencies were introduced
the code is committed

36. Final Engineering Principle

SecureAsset should prefer:
simple, controlled, explainable engineering over unnecessary technical complexity.
The goal is not to build the largest system.

The goal is to build the smallest system that convincingly demonstrates:
reliable data processing
+
meaningful AI reasoning
+
controlled financial actions
+
failure handling
+
measurable revenue recovery