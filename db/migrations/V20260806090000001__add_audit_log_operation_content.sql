ALTER TABLE qms.audit_log
    ADD COLUMN IF NOT EXISTS operation_content TEXT;
