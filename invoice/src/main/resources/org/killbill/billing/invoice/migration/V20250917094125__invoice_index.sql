CREATE INDEX idx_invoices_tenant_record ON invoices (tenant_record_id, record_id);
CREATE INDEX idx_invoice_history_tenant_record ON invoice_history (tenant_record_id, record_id);