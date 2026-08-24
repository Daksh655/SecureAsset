import argparse
import os
import random
import uuid
from datetime import datetime, timezone, timedelta
import psycopg2
from psycopg2.extras import execute_batch

def generate_uuid():
    return str(uuid.uuid4())

def get_db_connection():
    return psycopg2.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("DB_PORT", "5433"),
        dbname=os.getenv("DB_NAME", "secureasset"),
        user=os.getenv("DB_USER", "secureasset"),
        password=os.getenv("DB_PASSWORD", "secureasset_dev")
    )

def random_date(start=None, end=None):
    if end is None:
        end = datetime.now(timezone.utc)
    if start is None:
        start = end - timedelta(days=30)
    delta = end - start
    int_delta = (delta.days * 24 * 60 * 60) + delta.seconds
    random_second = random.randrange(int_delta) if int_delta > 0 else 0
    return start + timedelta(seconds=random_second)

def generate_dataset(num_customers, num_orders, num_payments, seed):
    random.seed(seed)
    
    customers = []
    orders = []
    payments = []
    
    # Generate Customers
    for i in range(num_customers):
        c_id = generate_uuid()
        customers.append({
            'id': c_id,
            'razorpay_customer_id': f"cust_{c_id[:12]}",
            'name': f"Customer {i}",
            'email': f"customer{i}@example.com",
            'phone': f"+91{random.randint(9000000000, 9999999999)}",
            'created_at': random_date(),
        })
        
    scenario_custs = customers[:6] if num_customers >= 6 else customers
    
    order_id_counter = 0
    payment_id_counter = 0
    
    def add_order(cust_id, amount, status, created_at):
        nonlocal order_id_counter
        o_id = generate_uuid()
        orders.append({
            'id': o_id,
            'razorpay_order_id': f"order_{o_id[:12]}",
            'customer_id': cust_id,
            'amount': amount,
            'currency': 'INR',
            'status': status,
            'created_at': created_at,
        })
        order_id_counter += 1
        return o_id

    def add_payment(o_id, cust_id, amount, status, reason, method, attempt, created_at, failed_at=None, captured_at=None):
        nonlocal payment_id_counter
        p_id = generate_uuid()
        payments.append({
            'id': p_id,
            'razorpay_payment_id': f"pay_{p_id[:12]}",
            'order_id': o_id,
            'customer_id': cust_id,
            'amount': amount,
            'currency': 'INR',
            'status': status,
            'failure_reason': reason,
            'method': method,
            'attempt_number': attempt,
            'created_at': created_at,
            'failed_at': failed_at,
            'captured_at': captured_at
        })
        payment_id_counter += 1
        return p_id

    # Controlled Scenarios
    # 1. high-value timeout recovery candidate
    if order_id_counter < num_orders and len(scenario_custs) > 0:
        c = scenario_custs[0]
        o_id = add_order(c['id'], 15000.00, 'FAILED', random_date(c['created_at']))
        if payment_id_counter < num_payments:
            p_time = random_date(orders[-1]['created_at'])
            add_payment(o_id, c['id'], 15000.00, 'FAILED', 'TIMEOUT', 'upi', 1, p_time, failed_at=p_time + timedelta(seconds=10))

    # 2. low-value insufficient-funds case
    if order_id_counter < num_orders and len(scenario_custs) > 1:
        c = scenario_custs[1]
        o_id = add_order(c['id'], 299.00, 'FAILED', random_date(c['created_at']))
        if payment_id_counter < num_payments:
            p_time = random_date(orders[-1]['created_at'])
            add_payment(o_id, c['id'], 299.00, 'FAILED', 'INSUFFICIENT_FUNDS', 'card', 1, p_time, failed_at=p_time + timedelta(seconds=10))
            
    # 3. repeated payment failure
    if order_id_counter < num_orders and len(scenario_custs) > 2:
        c = scenario_custs[2]
        o_id = add_order(c['id'], 2500.00, 'FAILED', random_date(c['created_at']))
        p_time1 = random_date(orders[-1]['created_at'])
        if payment_id_counter < num_payments:
            add_payment(o_id, c['id'], 2500.00, 'FAILED', 'BANK_DECLINE', 'upi', 1, p_time1, failed_at=p_time1 + timedelta(seconds=5))
        if payment_id_counter < num_payments:
            p_time2 = p_time1 + timedelta(minutes=15)
            add_payment(o_id, c['id'], 2500.00, 'FAILED', 'NETWORK_ERROR', 'upi', 2, p_time2, failed_at=p_time2 + timedelta(seconds=5))
        if payment_id_counter < num_payments:
            p_time3 = p_time2 + timedelta(hours=2)
            add_payment(o_id, c['id'], 2500.00, 'FAILED', 'TIMEOUT', 'upi', 3, p_time3, failed_at=p_time3 + timedelta(seconds=5))

    # 4. already recovered payment
    if order_id_counter < num_orders and len(scenario_custs) > 3:
        c = scenario_custs[3]
        o_id = add_order(c['id'], 4000.00, 'PAID', random_date(c['created_at']))
        p_time1 = random_date(orders[-1]['created_at'])
        if payment_id_counter < num_payments:
            add_payment(o_id, c['id'], 4000.00, 'FAILED', 'BANK_DECLINE', 'card', 1, p_time1, failed_at=p_time1 + timedelta(seconds=5))
        if payment_id_counter < num_payments:
            p_time2 = p_time1 + timedelta(hours=1)
            add_payment(o_id, c['id'], 4000.00, 'CAPTURED', None, 'card', 2, p_time2, captured_at=p_time2 + timedelta(seconds=5))

    # 5. checkout abandonment
    if order_id_counter < num_orders and len(scenario_custs) > 4:
        c = scenario_custs[4]
        add_order(c['id'], 8000.00, 'ABANDONED', random_date(c['created_at']))
        # no payments generated for abandoned order

    # 6. recurring-payment failure
    if order_id_counter < num_orders and len(scenario_custs) > 5:
        c = scenario_custs[5]
        o_id = add_order(c['id'], 999.00, 'FAILED', random_date(c['created_at']))
        if payment_id_counter < num_payments:
            p_time = random_date(orders[-1]['created_at'])
            add_payment(o_id, c['id'], 999.00, 'FAILED', 'BANK_DECLINE', 'emandate', 1, p_time, failed_at=p_time + timedelta(seconds=5))

    # Fill remaining orders
    while order_id_counter < num_orders:
        c = random.choice(customers)
        status = random.choices(['CREATED', 'ATTEMPTED', 'PAID', 'FAILED', 'ABANDONED', 'CANCELLED'],
                                weights=[5, 10, 50, 20, 10, 5])[0]
        amount = round(random.uniform(100, 20000), 2)
        add_order(c['id'], amount, status, random_date(c['created_at']))

    # Fill remaining payments
    valid_orders = [o for o in orders if o['status'] not in ('ABANDONED', 'CREATED')]
    if not valid_orders:
        valid_orders = orders
        
    while payment_id_counter < num_payments:
        o = random.choice(valid_orders)
        
        if o['status'] == 'PAID':
            status = 'CAPTURED'
            reason = None
        elif o['status'] == 'FAILED':
            status = 'FAILED'
            reason = random.choice(['TIMEOUT', 'INSUFFICIENT_FUNDS', 'BANK_DECLINE', 'NETWORK_ERROR', 'CUSTOMER_CANCELLED', 'UNKNOWN'])
        else:
            status = random.choice(['CREATED', 'AUTHORIZED', 'FAILED', 'REFUNDED'])
            reason = random.choice(['TIMEOUT', 'INSUFFICIENT_FUNDS', 'BANK_DECLINE', 'NETWORK_ERROR', 'CUSTOMER_CANCELLED', 'UNKNOWN']) if status == 'FAILED' else None

        method = random.choice(['upi', 'card', 'netbanking', 'wallet'])
        
        existing_pays = [p for p in payments if p['order_id'] == o['id']]
        attempt = len(existing_pays) + 1
        
        p_time = random_date(o['created_at'])
        failed_at = p_time + timedelta(seconds=random.randint(1, 60)) if status == 'FAILED' else None
        captured_at = p_time + timedelta(seconds=random.randint(1, 60)) if status == 'CAPTURED' else None
        
        add_payment(o['id'], o['customer_id'], o['amount'], status, reason, method, attempt, p_time, failed_at, captured_at)

    print(f"Generated {len(customers)} customers, {len(orders)} orders, {len(payments)} payments.")
    return customers, orders, payments

def insert_data(customers, orders, payments):
    conn = get_db_connection()
    conn.autocommit = False
    cur = conn.cursor()
    
    try:
        print("Clearing existing data to ensure a fresh dataset...")
        try:
            cur.execute("TRUNCATE TABLE customers CASCADE;")
        except psycopg2.errors.UndefinedTable:
            conn.rollback()
            print("Tables might not exist yet. Please run database migrations first.")
            raise
            
        print("Inserting customers...")
        execute_batch(cur, """
            INSERT INTO customers (id, razorpay_customer_id, name, email, phone, created_at, updated_at)
            VALUES (%(id)s, %(razorpay_customer_id)s, %(name)s, %(email)s, %(phone)s, %(created_at)s, %(created_at)s)
        """, customers)

        print("Inserting orders...")
        execute_batch(cur, """
            INSERT INTO orders (id, razorpay_order_id, customer_id, amount, currency, status, created_at, updated_at)
            VALUES (%(id)s, %(razorpay_order_id)s, %(customer_id)s, %(amount)s, %(currency)s, %(status)s, %(created_at)s, %(created_at)s)
        """, orders)
        
        print("Inserting payments...")
        execute_batch(cur, """
            INSERT INTO payments (id, razorpay_payment_id, order_id, customer_id, amount, currency, status, failure_reason, method, attempt_number, created_at, failed_at, captured_at, updated_at)
            VALUES (%(id)s, %(razorpay_payment_id)s, %(order_id)s, %(customer_id)s, %(amount)s, %(currency)s, %(status)s, %(failure_reason)s, %(method)s, %(attempt_number)s, %(created_at)s, %(failed_at)s, %(captured_at)s, %(created_at)s)
        """, payments)
        
        conn.commit()
        print("Data inserted successfully.")
    except Exception as e:
        conn.rollback()
        print(f"Error inserting data: {e}")
        raise
    finally:
        cur.close()
        conn.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate synthetic dataset for SecureAsset")
    parser.add_argument('--customers', type=int, default=100, help="Number of customers to generate")
    parser.add_argument('--orders', type=int, default=200, help="Number of orders to generate")
    parser.add_argument('--payments', type=int, default=300, help="Number of payments to generate")
    parser.add_argument('--seed', type=int, default=42, help="Random seed for reproducible generation")
    
    args = parser.parse_args()
    
    c, o, p = generate_dataset(args.customers, args.orders, args.payments, args.seed)
    insert_data(c, o, p)
