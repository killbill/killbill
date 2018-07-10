drop index bundles_key on bundles;
create unique index bundles_external_key on bundles(external_key, tenant_record_id);