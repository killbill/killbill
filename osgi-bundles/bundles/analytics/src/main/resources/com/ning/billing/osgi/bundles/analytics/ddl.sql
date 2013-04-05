/*! SET storage_engine=INNODB */;

-- Subscription events
drop table if exists bst;
create table bst (
  record_id int(11) unsigned not null auto_increment
, subscription_event_record_id int(11) unsigned default null
, bundle_id char(36) not null
, bundle_external_key varchar(50) not null
, subscription_id char(36) not null
, requested_timestamp datetime not null
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
, prev_state varchar(50) default null
, prev_business_active bool default true
, prev_start_date datetime default null
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
, next_state varchar(50) default null
, next_business_active bool default true
, next_start_date datetime default null
, next_end_date datetime default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bst_bundle_external_key on bst(bundle_external_key);
create index bst_account_id on bst(account_id);
create index bst_account_record_id on bst(account_record_id);
create index bst_tenant_account_record_id on bst(tenant_record_id, account_record_id);

-- Accounts
drop table if exists bac;
create table bac (
  record_id int(11) unsigned not null auto_increment
, email varchar(128) not null
, first_name_length int not null
, currency char(3) default null
, billing_cycle_day_local int default null
, payment_method_id char(36) default null
, time_zone varchar(50) default null
, locale varchar(5) default null
, address1 varchar(100) default null
, address2 varchar(100) default null
, company_name varchar(50) default null
, city varchar(50) default null
, state_or_province varchar(50) default null
, country varchar(50) default null
, postal_code varchar(16) default null
, phone varchar(25) default null
, migrated bool default false
, notified_for_invoices boolean not null
, balance numeric(10, 4) default 0
, last_invoice_date date default null
, last_payment_date datetime not null
, last_payment_status varchar(255) default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, updated_date datetime not null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bac_account_external_key on bac(account_external_key);
create index bac_account_id on bac(account_id);
create index bac_account_record_id on bac(account_record_id);
create index bac_tenant_account_record_id on bac(tenant_record_id, account_record_id);

-- Invoices
drop table if exists bin;
create table bin (
  record_id int(11) unsigned not null auto_increment
, invoice_record_id int(11) unsigned default null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_date date not null
, target_date date not null
, currency char(50) not null
, balance numeric(10, 4) default 0
, amount_paid numeric(10, 4) default 0
, amount_charged numeric(10, 4) default 0
, original_amount_charged numeric(10, 4) default 0
, amount_credited numeric(10, 4) default 0
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bin_invoice_record_id on bin(invoice_record_id);
create index bin_invoice_id on bin(invoice_id);
create index bin_account_id on bin(account_id);
create index bin_account_record_id on bin(account_record_id);
create index bin_tenant_account_record_id on bin(tenant_record_id, account_record_id);

-- Invoice adjustments (type REFUND_ADJ)
drop table if exists bia;
create table bia (
  record_id int(11) unsigned not null auto_increment
, invoice_item_record_id int(11) unsigned default null
, item_id char(36) not null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_created_date date not null
, invoice_date date not null
, invoice_target_date date not null
, invoice_currency char(50) not null
, invoice_balance numeric(10, 4) default 0
, invoice_amount_paid numeric(10, 4) default 0
, invoice_amount_charged numeric(10, 4) default 0
, invoice_original_amount_charged numeric(10, 4) default 0
, invoice_amount_credited numeric(10, 4) default 0
, item_type char(50) not null
, revenue_recognizable bool default true
, bundle_external_key varchar(50) default null
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
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bia_invoice_item_record_id on bia(invoice_item_record_id);
create index bia_item_id on bia(item_id);
create index bia_account_id on bia(account_id);
create index bia_account_record_id on bia(account_record_id);
create index bia_tenant_account_record_id on bia(tenant_record_id, account_record_id);

-- Invoice items (without adjustments, type EXTERNAL_CHARGE, FIXED and RECURRING)
drop table if exists bii;
create table bii (
  record_id int(11) unsigned not null auto_increment
, invoice_item_record_id int(11) unsigned default null
, item_id char(36) not null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_created_date date not null
, invoice_date date not null
, invoice_target_date date not null
, invoice_currency char(50) not null
, invoice_balance numeric(10, 4) default 0
, invoice_amount_paid numeric(10, 4) default 0
, invoice_amount_charged numeric(10, 4) default 0
, invoice_original_amount_charged numeric(10, 4) default 0
, invoice_amount_credited numeric(10, 4) default 0
, item_type char(50) not null
, revenue_recognizable bool default true
, bundle_external_key varchar(50) default null
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
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bii_invoice_item_record_id on bii(invoice_item_record_id);
create index bii_item_id on bii(item_id);
create index bii_account_id on bii(account_id);
create index bii_account_record_id on bii(account_record_id);
create index bii_tenant_account_record_id on bii(tenant_record_id, account_record_id);

-- Invoice items adjustments (type ITEM_ADJ)
drop table if exists biia;
create table biia (
  record_id int(11) unsigned not null auto_increment
, invoice_item_record_id int(11) unsigned default null
, item_id char(36) not null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_created_date date not null
, invoice_date date not null
, invoice_target_date date not null
, invoice_currency char(50) not null
, invoice_balance numeric(10, 4) default 0
, invoice_amount_paid numeric(10, 4) default 0
, invoice_amount_charged numeric(10, 4) default 0
, invoice_original_amount_charged numeric(10, 4) default 0
, invoice_amount_credited numeric(10, 4) default 0
, item_type char(50) not null
, revenue_recognizable bool default true
, bundle_external_key varchar(50) default null
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
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index biia_invoice_item_record_id on biia(invoice_item_record_id);
create index biia_item_id on biia(item_id);
create index biia_account_id on biia(account_id);
create index biia_account_record_id on biia(account_record_id);
create index biia_tenant_account_record_id on biia(tenant_record_id, account_record_id);

-- Account credits (type CBA_ADJ and CREDIT_ADJ)
drop table if exists biic;
create table biic (
  record_id int(11) unsigned not null auto_increment
, invoice_item_record_id int(11) unsigned default null
, item_id char(36) not null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_created_date date not null
, invoice_date date not null
, invoice_target_date date not null
, invoice_currency char(50) not null
, invoice_balance numeric(10, 4) default 0
, invoice_amount_paid numeric(10, 4) default 0
, invoice_amount_charged numeric(10, 4) default 0
, invoice_original_amount_charged numeric(10, 4) default 0
, invoice_amount_credited numeric(10, 4) default 0
, item_type char(50) not null
, revenue_recognizable bool default true
, bundle_external_key varchar(50) default null
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
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index biic_invoice_item_record_id on biic(invoice_item_record_id);
create index biic_item_id on biic(item_id);
create index biic_account_id on biic(account_id);
create index biic_account_record_id on biic(account_record_id);
create index biic_tenant_account_record_id on biic(tenant_record_id, account_record_id);

-- Invoice payments
drop table if exists bip;
create table bip (
  record_id int(11) unsigned not null auto_increment
, invoice_payment_record_id int(11) unsigned default null
, invoice_payment_id char(36) not null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_created_date date not null
, invoice_date date not null
, invoice_target_date date not null
, invoice_currency char(50) not null
, invoice_balance numeric(10, 4) default 0
, invoice_amount_paid numeric(10, 4) default 0
, invoice_amount_charged numeric(10, 4) default 0
, invoice_original_amount_charged numeric(10, 4) default 0
, invoice_amount_credited numeric(10, 4) default 0
, invoice_payment_type varchar(50) default null
, payment_number bigint default null
, linked_invoice_payment_id char(36) default null
, amount numeric(10, 4) default 0
, currency char(50) default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bip_invoice_payment_record_id on bip(invoice_payment_record_id);
create index bip_invoice_payment_id on bip(invoice_payment_id);
create index bip_account_id on bip(account_id);
create index bip_account_record_id on bip(account_record_id);
create index bip_tenant_account_record_id on bip(tenant_record_id, account_record_id);

-- Invoice refunds
drop table if exists bipr;
create table bipr (
  record_id int(11) unsigned not null auto_increment
, invoice_payment_record_id int(11) unsigned default null
, invoice_payment_id char(36) not null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_created_date date not null
, invoice_date date not null
, invoice_target_date date not null
, invoice_currency char(50) not null
, invoice_balance numeric(10, 4) default 0
, invoice_amount_paid numeric(10, 4) default 0
, invoice_amount_charged numeric(10, 4) default 0
, invoice_original_amount_charged numeric(10, 4) default 0
, invoice_amount_credited numeric(10, 4) default 0
, invoice_payment_type varchar(50) default null
, payment_number bigint default null
, linked_invoice_payment_id char(36) default null
, amount numeric(10, 4) default 0
, currency char(50) default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bipr_invoice_payment_record_id on bipr(invoice_payment_record_id);
create index bipr_invoice_payment_id on bipr(invoice_payment_id);
create index bipr_account_id on bipr(account_id);
create index bipr_account_record_id on bipr(account_record_id);
create index bipr_tenant_account_record_id on bipr(tenant_record_id, account_record_id);

-- Invoice payment chargebacks
drop table if exists bipc;
create table bipc (
  record_id int(11) unsigned not null auto_increment
, invoice_payment_record_id int(11) unsigned default null
, invoice_payment_id char(36) not null
, invoice_id char(36) not null
, invoice_number bigint default null
, invoice_created_date date not null
, invoice_date date not null
, invoice_target_date date not null
, invoice_currency char(50) not null
, invoice_balance numeric(10, 4) default 0
, invoice_amount_paid numeric(10, 4) default 0
, invoice_amount_charged numeric(10, 4) default 0
, invoice_original_amount_charged numeric(10, 4) default 0
, invoice_amount_credited numeric(10, 4) default 0
, invoice_payment_type varchar(50) default null
, payment_number bigint default null
, linked_invoice_payment_id char(36) default null
, amount numeric(10, 4) default 0
, currency char(50) default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bipc_invoice_payment_record_id on bipc(invoice_payment_record_id);
create index bipc_invoice_payment_id on bipc(invoice_payment_id);
create index bipc_account_id on bipc(account_id);
create index bipc_account_record_id on bipc(account_record_id);
create index bipc_tenant_account_record_id on bipc(tenant_record_id, account_record_id);

drop table if exists bos;
create table bos (
  record_id int(11) unsigned not null auto_increment
, blocking_state_record_id int(11) unsigned default null
, bundle_id char(36) not null
, bundle_external_key varchar(50) not null
, status varchar(50) not null
, start_date datetime default null
, end_date datetime default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bos_account_id on bos(account_id);
create index bos_account_record_id on bos(account_record_id);
create index bos_tenant_account_record_id on bos(tenant_record_id, account_record_id);

drop table if exists bac_tags;
create table bac_tags (
  record_id int(11) unsigned not null auto_increment
, tag_record_id int(11) unsigned default null
, name varchar(50) not null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bac_tags_account_id on bac_tags(account_id);
create index bac_tags_account_record_id on bac_tags(account_record_id);
create index bac_tags_tenant_account_record_id on bac_tags(tenant_record_id, account_record_id);

drop table if exists bin_tags;
create table bin_tags (
  record_id int(11) unsigned not null auto_increment
, tag_record_id int(11) unsigned default null
, invoice_id char(36) not null
, name varchar(50) not null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bin_tags_account_id on bin_tags(account_id);
create index bin_tags_account_record_id on bin_tags(account_record_id);
create index bin_tags_tenant_account_record_id on bin_tags(tenant_record_id, account_record_id);

drop table if exists bip_tags;
create table bip_tags (
  record_id int(11) unsigned not null auto_increment
, tag_record_id int(11) unsigned default null
, invoice_payment_id char(36) not null
, name varchar(50) not null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bip_tags_account_id on bip_tags(account_id);
create index bip_tags_account_record_id on bip_tags(account_record_id);
create index bip_tags_tenant_account_record_id on bip_tags(tenant_record_id, account_record_id);

drop table if exists bac_fields;
create table bac_fields (
  record_id int(11) unsigned not null auto_increment
, custom_field_record_id int(11) unsigned default null
, name varchar(50) not null
, value varchar(255) default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bac_fields_account_id on bac_fields(account_id);
create index bac_fields_account_record_id on bac_fields(account_record_id);
create index bac_fields_tenant_account_record_id on bac_fields(tenant_record_id, account_record_id);

drop table if exists bin_fields;
create table bin_fields (
  record_id int(11) unsigned not null auto_increment
, custom_field_record_id int(11) unsigned default null
, invoice_id char(36) not null
, name varchar(50) not null
, value varchar(255) default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bin_fields_account_id on bin_fields(account_id);
create index bin_fields_account_record_id on bin_fields(account_record_id);
create index bin_fields_tenant_account_record_id on bin_fields(tenant_record_id, account_record_id);

drop table if exists bip_fields;
create table bip_fields (
  record_id int(11) unsigned not null auto_increment
, custom_field_record_id int(11) unsigned default null
, invoice_payment_id char(36) not null
, name varchar(50) not null
, value varchar(255) default null
, created_date datetime not null
, created_by varchar(50) not null
, created_reason_code varchar(255) default null
, created_comments varchar(255) default null
, account_id char(36) not null
, account_name varchar(100) not null
, account_external_key varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index bip_fields_account_id on bip_fields(account_id);
create index bip_fields_account_record_id on bip_fields(account_record_id);
create index bip_fields_tenant_account_record_id on bip_fields(tenant_record_id, account_record_id);
