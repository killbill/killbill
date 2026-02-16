CREATE INDEX idx_custom_fields_tenant_record ON custom_fields (tenant_record_id, record_id);
CREATE INDEX idx_custom_field_history_tenant_record ON custom_field_history (tenant_record_id, record_id);
CREATE INDEX idx_tags_tenant_record ON tags (tenant_record_id, record_id);
CREATE INDEX idx_tag_history_tenant_record ON tag_history (tenant_record_id, record_id);
CREATE INDEX idx_tag_definitions_tenant_record ON tag_definitions (tenant_record_id, record_id);
CREATE INDEX idx_tag_definition_history_tenant_record ON tag_definition_history (tenant_record_id, record_id);