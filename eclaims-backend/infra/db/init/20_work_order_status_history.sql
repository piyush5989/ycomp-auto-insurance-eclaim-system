-- 20_work_order_status_history.sql
-- Track repair status changes for customer visibility and audit trail

CREATE TABLE IF NOT EXISTS workshops.work_order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    note TEXT,
    estimated_completion_date DATE,
    changed_by_user_id VARCHAR(255),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_status_history_work_order 
        FOREIGN KEY (work_order_id) 
        REFERENCES workshops.work_orders(id) 
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_work_order_status_history_work_order_id 
    ON workshops.work_order_status_history(work_order_id);
CREATE INDEX IF NOT EXISTS idx_work_order_status_history_changed_at 
    ON workshops.work_order_status_history(changed_at);

COMMENT ON TABLE workshops.work_order_status_history IS 'Audit trail of all repair status changes for transparency and customer tracking (FR9)';
