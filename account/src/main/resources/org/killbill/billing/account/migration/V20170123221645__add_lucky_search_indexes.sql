alter table accounts add index accounts_email_tenant_record_id(email, tenant_record_id);
alter table accounts add index accounts_company_name_tenant_record_id(company_name, tenant_record_id);
alter table accounts add index accounts_name_tenant_record_id(name, tenant_record_id);
