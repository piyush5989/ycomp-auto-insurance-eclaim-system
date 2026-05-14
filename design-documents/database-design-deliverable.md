# Database Design - Client Ready Deliverable

## Executive Summary

This document presents the comprehensive database design for the system, following enterprise-grade principles for scalability, performance, and data integrity. The design supports multi-tenant architecture with strong consistency and audit capabilities.

## 1. Database Architecture Overview

### 1.1 Design Principles
- **Schema-Based Multi-tenancy**: Logical separation using PostgreSQL schemas
- **ACID Compliance**: Strong consistency and transactional integrity
- **Audit Trail**: Complete change tracking for compliance
- **Performance Optimization**: Strategic indexing and partitioning
- **Data Security**: Encryption at rest and in transit
- **Scalability**: Horizontal scaling through read replicas

### 1.2 Technology Stack
- **Primary Database**: PostgreSQL 15+
- **Connection Pooling**: PgBouncer
- **Monitoring**: PostgreSQL Stats Collector
- **Backup Strategy**: WAL-E with Point-in-Time Recovery
- **High Availability**: Primary-Secondary with automatic failover

### 1.3 Schema Organization
```
├── claims - Core claims management
├── customers - Customer information
├── documents - Document storage metadata
├── workflow - Business process management
├── workshops - Workshop and service provider data
├── payments - Financial transactions
├── reporting - Analytics and reporting views
├── audit - System audit logs
├── notifications - User notifications
└── public - Shared reference data
```

## 2. Core Database Schemas

### 2.1 Claims Schema

#### 2.1.1 Claims Table
```sql
CREATE TABLE claims.claims (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_number           VARCHAR(20)     NOT NULL,
    customer_id             VARCHAR(100)    NOT NULL,
    customer_email          VARCHAR(255)    NOT NULL,
    vehicle_registration    VARCHAR(20)     NOT NULL,
    idempotency_key         VARCHAR(120)    UNIQUE,
    claim_type              VARCHAR(30)     NOT NULL CHECK (claim_type IN 
                           ('ACCIDENT', 'THEFT', 'FIRE', 'NATURAL_DISASTER', 'VANDALISM')),
    status                  VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN 
                           ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'ASSESSED', 
                            'APPROVED', 'REJECTED', 'SETTLED', 'CLOSED', 'WITHDRAWN')),
    incident_date           DATE            NOT NULL CHECK (incident_date <= CURRENT_DATE),
    incident_location       VARCHAR(500),
    description             TEXT            NOT NULL,
    police_report_filed     BOOLEAN         NOT NULL DEFAULT FALSE,
    police_report_number    VARCHAR(50),
    assigned_surveyor_id    VARCHAR(100),
    assigned_adjustor_id    VARCHAR(100),
    assessed_amount         DECIMAL(15,2)   CHECK (assessed_amount >= 0),
    approved_amount         DECIMAL(15,2)   CHECK (approved_amount >= 0),
    deductible_amount       DECIMAL(15,2)   DEFAULT 0 CHECK (deductible_amount >= 0),
    workshop_id             VARCHAR(100),
    rejection_reason        TEXT,
    fraud_flag              BOOLEAN         NOT NULL DEFAULT FALSE,
    fraud_reason            TEXT,
    priority_level          INTEGER         DEFAULT 3 CHECK (priority_level BETWEEN 1 AND 5),
    estimated_settlement    DATE,
    actual_settlement       DATE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_assessment_approved CHECK (
        approved_amount IS NULL OR 
        assessed_amount IS NULL OR 
        approved_amount <= assessed_amount
    ),
    CONSTRAINT chk_settlement_dates CHECK (
        actual_settlement IS NULL OR 
        estimated_settlement IS NULL OR 
        actual_settlement >= estimated_settlement
    )
);
```

#### 2.1.2 Claim History (Audit Trail)
```sql
CREATE TABLE claims.claim_history (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID        NOT NULL REFERENCES claims.claims(id) ON DELETE CASCADE,
    previous_status VARCHAR(30),
    new_status      VARCHAR(30) NOT NULL,
    changed_by      VARCHAR(100) NOT NULL,
    change_reason   TEXT,
    system_notes    TEXT,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    INDEX idx_claim_history_claim_id (claim_id),
    INDEX idx_claim_history_changed_at (changed_at DESC)
);
```

#### 2.1.3 Performance Indexes
```sql
-- Core performance indexes
CREATE INDEX CONCURRENTLY idx_claims_customer_id 
    ON claims.claims(customer_id);

CREATE INDEX CONCURRENTLY idx_claims_status_priority 
    ON claims.claims(status, priority_level DESC, created_at);

CREATE INDEX CONCURRENTLY idx_claims_policy_number 
    ON claims.claims(policy_number);

CREATE INDEX CONCURRENTLY idx_claims_incident_date 
    ON claims.claims(incident_date DESC);

-- Specialized indexes for different user roles
CREATE INDEX CONCURRENTLY idx_claims_surveyor_queue 
    ON claims.claims(assigned_surveyor_id, status) 
    WHERE status IN ('ASSIGNED', 'UNDER_REVIEW');

CREATE INDEX CONCURRENTLY idx_claims_adjustor_queue 
    ON claims.claims(assigned_adjustor_id, status) 
    WHERE status IN ('ASSESSED', 'APPROVED');

-- Fraud detection index
CREATE INDEX CONCURRENTLY idx_claims_fraud_investigation 
    ON claims.claims(fraud_flag, status, created_at) 
    WHERE fraud_flag = TRUE;
```

### 2.2 Documents Schema

#### 2.2.1 Documents Table
```sql
CREATE TABLE documents.documents (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id            UUID        NOT NULL REFERENCES claims.claims(id),
    original_filename   VARCHAR(255) NOT NULL,
    stored_filename     VARCHAR(255) NOT NULL UNIQUE,
    file_path          VARCHAR(1000) NOT NULL,
    file_size_bytes    BIGINT       NOT NULL CHECK (file_size_bytes > 0),
    mime_type          VARCHAR(100) NOT NULL,
    document_type      VARCHAR(50)  NOT NULL CHECK (document_type IN 
                      ('VEHICLE_PHOTOS', 'DAMAGE_PHOTOS', 'POLICE_REPORT', 
                       'INSURANCE_CARD', 'DRIVING_LICENSE', 'ESTIMATE', 
                       'INVOICE', 'RECEIPT', 'IDENTITY_PROOF', 'OTHER')),
    upload_status      VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED' CHECK (upload_status IN 
                      ('UPLOADING', 'UPLOADED', 'VERIFIED', 'REJECTED', 'ARCHIVED')),
    verification_status VARCHAR(20) DEFAULT 'PENDING' CHECK (verification_status IN 
                      ('PENDING', 'APPROVED', 'REJECTED', 'REQUIRES_REVIEW')),
    verification_notes TEXT,
    virus_scan_status  VARCHAR(20)  DEFAULT 'PENDING' CHECK (virus_scan_status IN 
                      ('PENDING', 'CLEAN', 'INFECTED', 'QUARANTINED')),
    uploaded_by        VARCHAR(100) NOT NULL,
    verified_by        VARCHAR(100),
    retention_until    DATE         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    INDEX idx_documents_claim_id (claim_id),
    INDEX idx_documents_type_status (document_type, upload_status),
    INDEX idx_documents_retention (retention_until)
);
```

### 2.3 Customers Schema

#### 2.3.1 Customer Profiles
```sql
CREATE TABLE customers.customer_profiles (
    customer_id         VARCHAR(100) PRIMARY KEY,
    email              VARCHAR(255) NOT NULL UNIQUE,
    phone_number       VARCHAR(20),
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    date_of_birth      DATE,
    address_line1      VARCHAR(200),
    address_line2      VARCHAR(200),
    city               VARCHAR(100),
    state              VARCHAR(100),
    postal_code        VARCHAR(20),
    country            VARCHAR(100) DEFAULT 'India',
    kyc_status         VARCHAR(20)  DEFAULT 'PENDING' CHECK (kyc_status IN 
                      ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')),
    risk_profile       VARCHAR(20)  DEFAULT 'STANDARD' CHECK (risk_profile IN 
                      ('LOW', 'STANDARD', 'HIGH', 'BLACKLISTED')),
    preferred_language VARCHAR(10)  DEFAULT 'en',
    communication_pref JSONB        DEFAULT '{"email": true, "sms": true, "push": true}',
    last_login         TIMESTAMPTZ,
    account_status     VARCHAR(20)  DEFAULT 'ACTIVE' CHECK (account_status IN 
                      ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    INDEX idx_customer_email (email),
    INDEX idx_customer_phone (phone_number),
    INDEX idx_customer_kyc_status (kyc_status),
    INDEX idx_customer_risk_profile (risk_profile)
);
```

### 2.4 Workshops Schema

#### 2.4.1 Workshop Directory
```sql
CREATE TABLE workshops.workshop_directory (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workshop_code       VARCHAR(20) NOT NULL UNIQUE,
    name               VARCHAR(200) NOT NULL,
    owner_name         VARCHAR(100),
    contact_email      VARCHAR(255),
    contact_phone      VARCHAR(20),
    address_line1      VARCHAR(200) NOT NULL,
    address_line2      VARCHAR(200),
    city               VARCHAR(100) NOT NULL,
    state              VARCHAR(100) NOT NULL,
    postal_code        VARCHAR(20)  NOT NULL,
    latitude           DECIMAL(10,8),
    longitude          DECIMAL(11,8),
    specialties        TEXT[],      -- Array of specialties
    supported_brands   TEXT[],      -- Array of vehicle brands
    capacity_per_day   INTEGER      DEFAULT 5 CHECK (capacity_per_day > 0),
    average_rating     DECIMAL(3,2) DEFAULT 0 CHECK (average_rating BETWEEN 0 AND 5),
    total_reviews      INTEGER      DEFAULT 0 CHECK (total_reviews >= 0),
    certification_level VARCHAR(20) DEFAULT 'BASIC' CHECK (certification_level IN 
                       ('BASIC', 'STANDARD', 'PREMIUM', 'AUTHORIZED')),
    onboarding_date    DATE         NOT NULL,
    contract_expiry    DATE,
    status             VARCHAR(20)  DEFAULT 'ACTIVE' CHECK (status IN 
                      ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'TERMINATED')),
    payment_terms      VARCHAR(50)  DEFAULT '30_DAYS',
    insurance_valid_until DATE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    INDEX idx_workshop_location (city, state),
    INDEX idx_workshop_rating_capacity (average_rating DESC, capacity_per_day DESC),
    INDEX idx_workshop_specialties USING GIN(specialties),
    INDEX idx_workshop_brands USING GIN(supported_brands),
    INDEX idx_workshop_geo (latitude, longitude)  -- For location-based searches
);
```

### 2.5 Payments Schema

#### 2.5.1 Payment Transactions
```sql
CREATE TABLE payments.payment_transactions (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id            UUID         NOT NULL REFERENCES claims.claims(id),
    transaction_ref     VARCHAR(100) NOT NULL UNIQUE,
    payment_type        VARCHAR(30)  NOT NULL CHECK (payment_type IN 
                       ('CLAIM_SETTLEMENT', 'DEDUCTIBLE', 'EXCESS', 'REFUND')),
    amount              DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3)   DEFAULT 'INR',
    payment_method      VARCHAR(30)  CHECK (payment_method IN 
                       ('BANK_TRANSFER', 'UPI', 'CHEQUE', 'DD', 'WALLET')),
    recipient_type      VARCHAR(20)  NOT NULL CHECK (recipient_type IN 
                       ('CUSTOMER', 'WORKSHOP', 'VENDOR', 'INTERNAL')),
    recipient_id        VARCHAR(100) NOT NULL,
    recipient_name      VARCHAR(200) NOT NULL,
    recipient_account   VARCHAR(100),
    bank_details        JSONB,
    payment_status      VARCHAR(20)  DEFAULT 'INITIATED' CHECK (payment_status IN 
                       ('INITIATED', 'PROCESSING', 'COMPLETED', 'FAILED', 
                        'CANCELLED', 'REFUNDED')),
    gateway_response    JSONB,
    processing_fee      DECIMAL(10,2) DEFAULT 0,
    tax_amount          DECIMAL(10,2) DEFAULT 0,
    net_amount          DECIMAL(15,2) NOT NULL,
    initiated_by        VARCHAR(100) NOT NULL,
    approved_by         VARCHAR(100),
    processed_at        TIMESTAMPTZ,
    failed_reason       TEXT,
    reconciliation_date DATE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    INDEX idx_payment_claim_id (claim_id),
    INDEX idx_payment_status (payment_status),
    INDEX idx_payment_date (processed_at DESC),
    INDEX idx_payment_recipient (recipient_type, recipient_id)
);
```

### 2.6 Notifications Schema

#### 2.6.1 User Notifications
```sql
CREATE TABLE notifications.user_notifications (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL CHECK (notification_type IN 
                   ('CLAIM_SUBMITTED', 'CLAIM_APPROVED', 'CLAIM_REJECTED', 
                    'SURVEYOR_ASSIGNED', 'PAYMENT_PROCESSED', 'DOCUMENT_REQUIRED',
                    'STATUS_UPDATE', 'REMINDER', 'ALERT')),
    title           VARCHAR(200) NOT NULL,
    message         TEXT         NOT NULL,
    data_payload    JSONB,       -- Additional structured data
    channels        TEXT[]       DEFAULT ARRAY['IN_APP'], -- email, sms, push, in_app
    priority        VARCHAR(10)  DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    status          VARCHAR(20)  DEFAULT 'SENT' CHECK (status IN 
                   ('PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED')),
    related_entity_type VARCHAR(50), -- 'claim', 'payment', etc.
    related_entity_id   UUID,
    scheduled_for   TIMESTAMPTZ  DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    INDEX idx_notifications_user_status (user_id, status),
    INDEX idx_notifications_priority (priority, scheduled_for),
    INDEX idx_notifications_entity (related_entity_type, related_entity_id)
);
```

### 2.7 Workflow Schema

#### 2.7.1 Workflow States
```sql
CREATE TABLE workflow.workflow_states (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(50)  NOT NULL, -- 'claim', 'payment', etc.
    entity_id           UUID         NOT NULL,
    current_state       VARCHAR(50)  NOT NULL,
    previous_state      VARCHAR(50),
    workflow_version    VARCHAR(10)  DEFAULT '1.0',
    state_data          JSONB        DEFAULT '{}',
    assignee_id         VARCHAR(100),
    assignee_role       VARCHAR(50),
    due_date            TIMESTAMPTZ,
    escalation_level    INTEGER      DEFAULT 0,
    escalated_to        VARCHAR(100),
    locked_by           VARCHAR(100), -- Pessimistic locking for concurrent updates
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    UNIQUE(entity_type, entity_id),
    INDEX idx_workflow_assignee (assignee_id, current_state),
    INDEX idx_workflow_due_date (due_date),
    INDEX idx_workflow_escalation (escalation_level, due_date)
);
```

### 2.8 Audit Schema

#### 2.8.1 System Audit Log
```sql
CREATE TABLE audit.system_audit_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name      VARCHAR(100) NOT NULL,
    record_id       UUID         NOT NULL,
    operation       VARCHAR(10)  NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    old_values      JSONB,
    new_values      JSONB,
    changed_fields  TEXT[],
    user_id         VARCHAR(100),
    session_id      VARCHAR(100),
    ip_address      INET,
    user_agent      TEXT,
    application     VARCHAR(50)  DEFAULT 'claims-system',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    
    INDEX idx_audit_table_record (table_name, record_id),
    INDEX idx_audit_user_date (user_id, created_at DESC),
    INDEX idx_audit_operation (operation, created_at DESC)
);

-- Partition by month for performance
ALTER TABLE audit.system_audit_log 
PARTITION BY RANGE (created_at);
```

## 3. Database Performance Optimization

### 3.1 Indexing Strategy

#### 3.1.1 Composite Indexes for Common Queries
```sql
-- Customer dashboard - claims list
CREATE INDEX CONCURRENTLY idx_claims_customer_dashboard 
    ON claims.claims(customer_id, status, created_at DESC);

-- Surveyor workqueue
CREATE INDEX CONCURRENTLY idx_claims_surveyor_workqueue 
    ON claims.claims(assigned_surveyor_id, status, priority_level DESC, created_at)
    WHERE status IN ('ASSIGNED', 'UNDER_REVIEW');

-- Payment reconciliation
CREATE INDEX CONCURRENTLY idx_payments_reconciliation 
    ON payments.payment_transactions(payment_status, processed_at, reconciliation_date)
    WHERE payment_status = 'COMPLETED' AND reconciliation_date IS NULL;
```

#### 3.1.2 Partial Indexes for Efficiency
```sql
-- Active workshops only
CREATE INDEX CONCURRENTLY idx_workshops_active_location 
    ON workshops.workshop_directory(city, state, average_rating DESC)
    WHERE status = 'ACTIVE';

-- Unread notifications
CREATE INDEX CONCURRENTLY idx_notifications_unread 
    ON notifications.user_notifications(user_id, priority, scheduled_for)
    WHERE status IN ('SENT', 'DELIVERED');
```

### 3.2 Partitioning Strategy

#### 3.2.1 Time-Based Partitioning
```sql
-- Partition audit logs by month
CREATE TABLE audit.system_audit_log_2026_05 PARTITION OF audit.system_audit_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

-- Partition notifications by month
CREATE TABLE notifications.user_notifications_2026_05 PARTITION OF notifications.user_notifications
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
```

## 4. Data Security & Compliance

### 4.1 Data Encryption
```sql
-- Enable column-level encryption for sensitive data
ALTER TABLE customers.customer_profiles 
    ADD COLUMN phone_encrypted BYTEA,
    ADD COLUMN email_encrypted BYTEA;

-- Create encryption functions
CREATE OR REPLACE FUNCTION encrypt_pii(data TEXT) 
RETURNS BYTEA AS $$
BEGIN
    RETURN pgp_sym_encrypt(data, current_setting('app.encryption_key'));
END;
$$ LANGUAGE plpgsql;
```

### 4.2 Row-Level Security (RLS)
```sql
-- Enable RLS for customer data
ALTER TABLE customers.customer_profiles ENABLE ROW LEVEL SECURITY;

-- Policy for customers to see only their own data
CREATE POLICY customer_own_data ON customers.customer_profiles
    FOR ALL TO claims_app_role
    USING (customer_id = current_setting('app.current_customer_id'));
```

### 4.3 Data Retention Policies
```sql
-- Function to archive old records
CREATE OR REPLACE FUNCTION archive_old_records() 
RETURNS INTEGER AS $$
DECLARE
    archived_count INTEGER := 0;
BEGIN
    -- Archive claims older than 7 years
    WITH archived AS (
        DELETE FROM claims.claims 
        WHERE status = 'CLOSED' 
        AND updated_at < NOW() - INTERVAL '7 years'
        RETURNING *
    )
    INSERT INTO claims.claims_archive SELECT * FROM archived;
    
    GET DIAGNOSTICS archived_count = ROW_COUNT;
    RETURN archived_count;
END;
$$ LANGUAGE plpgsql;
```

## 5. Database Monitoring & Maintenance

### 5.1 Performance Monitoring Views
```sql
-- Slow query monitoring
CREATE VIEW monitoring.slow_queries AS
SELECT 
    query,
    calls,
    total_time,
    mean_time,
    min_time,
    max_time,
    stddev_time
FROM pg_stat_statements
WHERE mean_time > 1000  -- Queries slower than 1 second
ORDER BY mean_time DESC;

-- Index usage analysis
CREATE VIEW monitoring.index_usage AS
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_tup_read,
    idx_tup_fetch,
    idx_scan
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

### 5.2 Health Check Queries
```sql
-- Database connection health
SELECT 
    count(*) as active_connections,
    max(now() - query_start) as longest_running_query
FROM pg_stat_activity 
WHERE state = 'active';

-- Table size monitoring
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables 
WHERE schemaname NOT IN ('information_schema', 'pg_catalog')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

## 6. Backup & Recovery Strategy

### 6.1 Backup Configuration
```bash
# Full backup daily at 2 AM
0 2 * * * pg_dump -h localhost -U backup_user -d claims_db | gzip > /backups/full_backup_$(date +\%Y\%m\%d).sql.gz

# Point-in-time recovery with WAL archiving
archive_mode = on
archive_command = 'cp %p /var/lib/postgresql/wal_archive/%f'
wal_level = replica
```

### 6.2 Recovery Procedures
```sql
-- Point-in-time recovery
SELECT pg_create_restore_point('before_deployment_v2.1');

-- Verify backup integrity
SELECT pg_verify_checksums();
```

## 7. Scalability Considerations

### 7.1 Read Replicas Configuration
```sql
-- Connection routing for read queries
-- Primary: All writes and critical reads
-- Replica 1: Reporting queries
-- Replica 2: Analytics and dashboard queries

-- Example connection string for read replica
host=replica1.claims-db.internal port=5432 dbname=claims_db user=readonly
```

### 7.2 Connection Pooling
```ini
# PgBouncer configuration
[databases]
claims_db = host=localhost port=5432 dbname=claims_db

[pgbouncer]
listen_port = 6432
listen_addr = *
auth_type = md5
pool_mode = transaction
max_client_conn = 100
default_pool_size = 20
```

## 8. Migration Strategy

### 8.1 Schema Versioning
```sql
CREATE TABLE public.schema_migrations (
    version         VARCHAR(20) PRIMARY KEY,
    description     VARCHAR(200),
    applied_at      TIMESTAMPTZ DEFAULT NOW(),
    checksum        VARCHAR(64)
);
```

### 8.2 Zero-Downtime Migrations
```sql
-- Example: Adding a new column with default value
-- Step 1: Add column as nullable
ALTER TABLE claims.claims ADD COLUMN new_field VARCHAR(50);

-- Step 2: Populate with default values
UPDATE claims.claims SET new_field = 'default_value' WHERE new_field IS NULL;

-- Step 3: Add NOT NULL constraint
ALTER TABLE claims.claims ALTER COLUMN new_field SET NOT NULL;
```

---

## Appendix A: Database Schema ERD

```
[Claims] 1---* [ClaimHistory]
[Claims] 1---* [Documents]
[Claims] *---1 [Customers]
[Claims] *---1 [Workshops]
[Claims] 1---* [Payments]
[Claims] 1---* [WorkflowStates]
[Customers] 1---* [Notifications]
```

## Appendix B: Sample Queries

### High-Performance Dashboard Query
```sql
-- Customer claims dashboard with aggregations
WITH claim_stats AS (
    SELECT 
        customer_id,
        COUNT(*) as total_claims,
        COUNT(*) FILTER (WHERE status = 'SETTLED') as settled_claims,
        SUM(approved_amount) FILTER (WHERE status = 'SETTLED') as total_settled_amount,
        AVG(EXTRACT(days FROM (actual_settlement - created_at))) as avg_settlement_days
    FROM claims.claims
    WHERE customer_id = $1
    GROUP BY customer_id
)
SELECT c.*, cs.* 
FROM customers.customer_profiles c
LEFT JOIN claim_stats cs ON c.customer_id = cs.customer_id
WHERE c.customer_id = $1;
```

---