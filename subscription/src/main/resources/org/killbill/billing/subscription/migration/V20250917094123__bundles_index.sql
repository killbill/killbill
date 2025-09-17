CREATE INDEX idx_bundles_tenant_record ON bundles (tenant_record_id, record_id);
CREATE INDEX idx_bundle_history_tenant_record ON bundle_history (tenant_record_id, record_id);
