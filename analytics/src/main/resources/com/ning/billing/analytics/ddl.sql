/*! SET storage_engine=INNODB */;

drop table if exists bst;
create table bst (
  record_id int(11) unsigned not null auto_increment
, total_ordering bigint default 0
, bundle_id char(36) not null
, account_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, subscription_id char(36) not null
, requested_timestamp bigint not null
, event varchar(50) not null
, prev_product_name varchar(50) default null
, prev_product_type varchar(50) default null
, prev_product_category varchar(50) default null
, prev_slug varchar(50) default null
, prev_phase varchar(50) default null
, prev_billing_period varchar(50) default null
, prev_price numeric(10, 4) default 0
, prev_price_list varchar(50) default null
, prev_mrr numeric(10, 4) default 0
, prev_currency varchar(50) default null
, prev_start_date bigint default null
, prev_state varchar(50) default null
, next_product_name varchar(50) default null
, next_product_type varchar(50) default null
, next_product_category varchar(50) default null
, next_slug varchar(50) default null
, next_phase varchar(50) default null
, next_billing_period varchar(50) default null
, next_price numeric(10, 4) default 0
, next_price_list varchar(50) default null
, next_mrr numeric(10, 4) default 0
, next_currency varchar(50) default null
, next_start_date bigint default null
, next_state varchar(50) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Business Subscription Transitions, track bundles lifecycle';
create index bst_key_index on bst (external_key, requested_timestamp asc);
create index bst_tenant_account_record_id on bst(tenant_record_id, account_record_id);

drop table if exists bac;
create table bac (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null
, name varchar(100) not null
, created_date bigint not null
, updated_date bigint not null
, balance numeric(10, 4) default 0
, last_invoice_date date default null
, total_invoice_balance numeric(10, 4) default 0
, last_payment_status varchar(255) default null
, payment_method varchar(50) default null
, credit_card_type varchar(50) default null
, billing_address_country varchar(50) default null
, currency char(50) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Business ACcounts, keep a record of all accounts';
create unique index bac_key_index on bac (account_key);
create index bac_tenant_account_record_id on bac(tenant_record_id, account_record_id);

drop table if exists bin;
create table bin (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, invoice_number bigint default null
, created_date bigint not null
, updated_date bigint not null
, account_id char(36) not null
, account_key varchar(50) not null
, invoice_date date not null
, target_date date not null
, currency char(50) not null
, balance numeric(10, 4) default 0 comment 'amount_charged - amount_paid - amount_credited'
, amount_paid numeric(10, 4) default 0 comment 'Sums of the successful payments made for this invoice minus the refunds associated with this invoice'
, amount_charged numeric(10, 4) default 0 comment 'Sums of the invoice items amount'
, amount_credited numeric(10, 4) default 0 comment 'Sums of the credit items'
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Business INvoices, keep a record of generated invoices';
create unique index bin_key_index on bin (invoice_id);
create index bin_tenant_account_record_id on bin(tenant_record_id, account_record_id);

drop table if exists bii;
create table bii (
  record_id int(11) unsigned not null auto_increment
, item_id char(36) not null
, created_date bigint not null
, updated_date bigint not null
, invoice_id char(36) not null
, item_type char(50) not null comment 'e.g. FIXED or RECURRING'
, external_key varchar(50) default null comment 'Bundle external key (could be null for certain items)'
, product_name varchar(50) default null
, product_type varchar(50) default null
, product_category varchar(50) default null
, slug varchar(50) default null
, phase varchar(50) default null
, billing_period varchar(50) default null
, start_date date default null
, end_date date default null
, amount numeric(10, 4) default 0
, currency char(50) default null
, linked_item_id char(36) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Business Invoice Items, keep a record of all invoice items';
create unique index bii_key_index on bii (item_id);
create index bii_tenant_account_record_id on bii(tenant_record_id, account_record_id);

drop table if exists bip;
create table bip (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, created_date bigint not null
, updated_date bigint not null
, ext_first_payment_ref_id varchar(255) default null
, ext_second_payment_ref_id varchar(255) default null
, account_key varchar(50) not null comment 'Account external key'
, invoice_id char(36) not null
, effective_date bigint default null
, amount numeric(10, 4) default 0
, currency char(50) default null
, payment_error varchar(255) default null
, processing_status varchar(50) default null
, requested_amount numeric(10, 4) default 0
, plugin_name varchar(50) default null
, payment_type varchar(50) default null
, payment_method varchar(50) default null
, card_type varchar(50) default null
, card_country varchar(50) default null
, invoice_payment_type varchar(50) default null
, linked_invoice_payment_id char(36) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Business Invoice Payments, track all payments';
create unique index bip_key_index on bip (payment_id);
create index bip_tenant_account_record_id on bip(tenant_record_id, account_record_id);

drop table if exists bos;
create table bos (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, status varchar(50) not null
, start_date bigint default null
, end_date bigint default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Business Overdue Status, historical bundles overdue status';
create index bos_tenant_account_record_id on bos(tenant_record_id, account_record_id);

drop table if exists bac_tags;
create table bac_tags (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Tags associated to accounts';
create index bac_tags_tenant_account_record_id on bac_tags(tenant_record_id, account_record_id);

drop table if exists bac_fields;
create table bac_fields (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Custom fields associated to accounts';
create index bac_fields_tenant_account_record_id on bac_fields(tenant_record_id, account_record_id);

drop table if exists bst_tags;
create table bst_tags (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Tags associated to bundles';
create index bst_tags_tenant_account_record_id on bst_tags(tenant_record_id, account_record_id);

drop table if exists bst_fields;
create table bst_fields (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Custom fields associated to bundles';
create index bst_fields_tenant_account_record_id on bst_fields(tenant_record_id, account_record_id);

drop table if exists bin_tags;
create table bin_tags (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Tags associated to invoices';
create index bin_tags_tenant_account_record_id on bin_tags(tenant_record_id, account_record_id);

drop table if exists bin_fields;
create table bin_fields (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Custom fields associated to invoices';
create index bin_fields_tenant_account_record_id on bin_fields(tenant_record_id, account_record_id);

drop table if exists bip_tags;
create table bip_tags (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Tags associated to payments';
create index bip_tags_tenant_account_record_id on bip_tags(tenant_record_id, account_record_id);

drop table if exists bip_fields;
create table bip_fields (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) comment 'Custom fields associated to payments';
create index bip_fields_tenant_account_record_id on bip_fields(tenant_record_id, account_record_id);
