alter table subscriptions add column external_key varchar(255) after bundle_id;
update subscriptions set external_key=id;
alter table subscriptions modify external_key varchar(255) not null;
create unique index subscriptions_external_key on subscriptions(external_key, tenant_record_id);

alter table subscription_history add column external_key varchar(255) after bundle_id;
update subscription_history set external_key=id;
alter table subscription_history modify external_key varchar(255) not null;