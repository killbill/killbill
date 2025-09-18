CREATE INDEX idx_payments_tenant_record ON payments (tenant_record_id, record_id);
CREATE INDEX idx_payment_history_tenant_record ON payment_history (tenant_record_id, record_id);
CREATE INDEX idx_payment_methods_tenant_record ON payment_methods (tenant_record_id, record_id);
CREATE INDEX idx_payment_method_history_tenant_record ON payment_method_history (tenant_record_id, record_id);