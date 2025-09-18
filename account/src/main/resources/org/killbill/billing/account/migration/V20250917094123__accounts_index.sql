CREATE INDEX idx_accounts_tenant_record ON accounts (tenant_record_id, record_id);
CREATE INDEX idx_account_history_tenant_record ON account_history (tenant_record_id, record_id);