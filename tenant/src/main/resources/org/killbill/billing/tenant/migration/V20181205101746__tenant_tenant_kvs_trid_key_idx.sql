CREATE INDEX tenant_kvs_trid_key ON tenant_kvs(tenant_record_id, tenant_key);
DROP INDEX tenant_kvs_key ON tenant_kvs;
