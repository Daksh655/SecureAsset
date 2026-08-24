CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- CUSTOMERS
-- ============================================================

CREATE TABLE customers (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           razorpay_customer_id VARCHAR(100) UNIQUE,
                           name VARCHAR(150) NOT NULL,
                           email VARCHAR(255),
                           phone VARCHAR(30),
                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_email
    ON customers(email);


-- ============================================================
-- ORDERS
-- ============================================================

CREATE TABLE orders (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        razorpay_order_id VARCHAR(100) UNIQUE,
                        customer_id UUID NOT NULL,
                        amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
                        currency CHAR(3) NOT NULL DEFAULT 'INR',
                        status VARCHAR(30) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT fk_orders_customer
                            FOREIGN KEY (customer_id)
                                REFERENCES customers(id)
);

CREATE INDEX idx_orders_customer
    ON orders(customer_id);

CREATE INDEX idx_orders_status
    ON orders(status);

CREATE INDEX idx_orders_created_at
    ON orders(created_at);


-- ============================================================
-- PAYMENTS
-- ============================================================

CREATE TABLE payments (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          razorpay_payment_id VARCHAR(100) UNIQUE,
                          order_id UUID NOT NULL,
                          customer_id UUID NOT NULL,
                          amount NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
                          currency CHAR(3) NOT NULL DEFAULT 'INR',
                          status VARCHAR(30) NOT NULL,
                          failure_reason VARCHAR(255),
                          method VARCHAR(50),
                          attempt_number INTEGER NOT NULL DEFAULT 1 CHECK (attempt_number >= 1),
                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          failed_at TIMESTAMPTZ,
                          captured_at TIMESTAMPTZ,
                          updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT fk_payments_order
                              FOREIGN KEY (order_id)
                                  REFERENCES orders(id),

                          CONSTRAINT fk_payments_customer
                              FOREIGN KEY (customer_id)
                                  REFERENCES customers(id)
);

CREATE INDEX idx_payments_order
    ON payments(order_id);

CREATE INDEX idx_payments_customer
    ON payments(customer_id);

CREATE INDEX idx_payments_status
    ON payments(status);

CREATE INDEX idx_payments_failure_reason
    ON payments(failure_reason);

CREATE INDEX idx_payments_created_at
    ON payments(created_at);

CREATE INDEX idx_payments_status_created_at
    ON payments(status, created_at);


-- ============================================================
-- RECOVERY CASES
-- ============================================================

CREATE TABLE recovery_cases (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                customer_id UUID NOT NULL,
                                order_id UUID,
                                payment_id UUID,
                                problem_type VARCHAR(40) NOT NULL,
                                risk_amount NUMERIC(14,2) NOT NULL CHECK (risk_amount >= 0),
                                recovery_score INTEGER NOT NULL DEFAULT 0
                                    CHECK (recovery_score BETWEEN 0 AND 100),
                                priority VARCHAR(20) NOT NULL,
                                eligibility VARCHAR(20) NOT NULL,
                                status VARCHAR(30) NOT NULL DEFAULT 'NEW',
                                agent_status VARCHAR(30) NOT NULL DEFAULT 'NOT_ANALYZED',
                                agent_recommendation VARCHAR(50),
                                agent_confidence NUMERIC(5,2)
                                    CHECK (
                                        agent_confidence IS NULL
                                            OR agent_confidence BETWEEN 0 AND 100
                                        ),
                                agent_reason TEXT,
                                detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                analyzed_at TIMESTAMPTZ,
                                resolved_at TIMESTAMPTZ,
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT fk_recovery_cases_customer
                                    FOREIGN KEY (customer_id)
                                        REFERENCES customers(id),

                                CONSTRAINT fk_recovery_cases_order
                                    FOREIGN KEY (order_id)
                                        REFERENCES orders(id),

                                CONSTRAINT fk_recovery_cases_payment
                                    FOREIGN KEY (payment_id)
                                        REFERENCES payments(id)
);

CREATE INDEX idx_recovery_cases_customer
    ON recovery_cases(customer_id);

CREATE INDEX idx_recovery_cases_order
    ON recovery_cases(order_id);

CREATE INDEX idx_recovery_cases_payment
    ON recovery_cases(payment_id);

CREATE INDEX idx_recovery_cases_priority
    ON recovery_cases(priority);

CREATE INDEX idx_recovery_cases_status
    ON recovery_cases(status);

CREATE INDEX idx_recovery_cases_problem_type
    ON recovery_cases(problem_type);

CREATE INDEX idx_recovery_cases_score
    ON recovery_cases(recovery_score);

CREATE INDEX idx_recovery_cases_detected_at
    ON recovery_cases(detected_at);

CREATE INDEX idx_recovery_cases_queue
    ON recovery_cases(priority, status, recovery_score);


-- ============================================================
-- RECOVERY ACTIONS
-- ============================================================

CREATE TABLE recovery_actions (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  recovery_case_id UUID NOT NULL,
                                  action_type VARCHAR(50) NOT NULL,
                                  amount NUMERIC(14,2)
                                      CHECK (amount IS NULL OR amount >= 0),
                                  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                                  approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED',
                                  razorpay_reference VARCHAR(150),
                                  result TEXT,
                                  error_code VARCHAR(100),
                                  error_message TEXT,
                                  requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  approved_at TIMESTAMPTZ,
                                  executed_at TIMESTAMPTZ,
                                  completed_at TIMESTAMPTZ,

                                  CONSTRAINT fk_recovery_actions_case
                                      FOREIGN KEY (recovery_case_id)
                                          REFERENCES recovery_cases(id)
);

CREATE INDEX idx_recovery_actions_case
    ON recovery_actions(recovery_case_id);

CREATE INDEX idx_recovery_actions_type
    ON recovery_actions(action_type);

CREATE INDEX idx_recovery_actions_status
    ON recovery_actions(status);

CREATE INDEX idx_recovery_actions_requested_at
    ON recovery_actions(requested_at);

CREATE INDEX idx_recovery_actions_razorpay_reference
    ON recovery_actions(razorpay_reference);


-- ============================================================
-- AUDIT LOGS
-- ============================================================

CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            recovery_case_id UUID,
                            recovery_action_id UUID,
                            event_type VARCHAR(50) NOT NULL,
                            actor_type VARCHAR(30) NOT NULL,
                            tool_name VARCHAR(100),
                            input_data JSONB,
                            output_data JSONB,
                            message TEXT,
                            success BOOLEAN NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            CONSTRAINT fk_audit_logs_case
                                FOREIGN KEY (recovery_case_id)
                                    REFERENCES recovery_cases(id),

                            CONSTRAINT fk_audit_logs_action
                                FOREIGN KEY (recovery_action_id)
                                    REFERENCES recovery_actions(id)
);

CREATE INDEX idx_audit_logs_case
    ON audit_logs(recovery_case_id);

CREATE INDEX idx_audit_logs_action
    ON audit_logs(recovery_action_id);

CREATE INDEX idx_audit_logs_event_type
    ON audit_logs(event_type);

CREATE INDEX idx_audit_logs_actor_type
    ON audit_logs(actor_type);

CREATE INDEX idx_audit_logs_created_at
    ON audit_logs(created_at);