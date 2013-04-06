/*! SET storage_engine=INNODB */;

drop table if exists old_bst;
create table old_bst (
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
);
create index old_bst_key_index on old_bst (external_key, requested_timestamp asc);
create index old_bst_tenant_account_record_id on old_bst(tenant_record_id, account_record_id);

drop table if exists old_bac;
create table old_bac (
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
);
create unique index old_bac_key_index on old_bac (account_key);
create index old_bac_tenant_account_record_id on old_bac(tenant_record_id, account_record_id);

drop table if exists old_bin;
create table old_bin (
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
);
create unique index old_bin_key_index on old_bin (invoice_id);
create index old_bin_tenant_account_record_id on old_bin(tenant_record_id, account_record_id);

drop table if exists old_bii;
create table old_bii (
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
);
create unique index old_bii_key_index on old_bii (item_id);
create index old_bii_tenant_account_record_id on old_bii(tenant_record_id, account_record_id);

drop table if exists old_bip;
create table old_bip (
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
);
create unique index old_bip_key_index on old_bip (payment_id);
create index old_bip_tenant_account_record_id on old_bip(tenant_record_id, account_record_id);

drop table if exists old_bos;
create table old_bos (
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
);
create index old_bos_tenant_account_record_id on old_bos(tenant_record_id, account_record_id);

drop table if exists old_bac_tags;
create table old_bac_tags (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bac_tags_tenant_account_record_id on old_bac_tags(tenant_record_id, account_record_id);

drop table if exists old_bac_fields;
create table old_bac_fields (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bac_fields_tenant_account_record_id on old_bac_fields(tenant_record_id, account_record_id);

drop table if exists old_bst_tags;
create table old_bst_tags (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bst_tags_tenant_account_record_id on old_bst_tags(tenant_record_id, account_record_id);

drop table if exists old_bst_fields;
create table old_bst_fields (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bst_fields_tenant_account_record_id on old_bst_fields(tenant_record_id, account_record_id);

drop table if exists old_bin_tags;
create table old_bin_tags (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bin_tags_tenant_account_record_id on old_bin_tags(tenant_record_id, account_record_id);

drop table if exists old_bin_fields;
create table old_bin_fields (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bin_fields_tenant_account_record_id on old_bin_fields(tenant_record_id, account_record_id);

drop table if exists old_bip_tags;
create table old_bip_tags (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, name varchar(50) not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bip_tags_tenant_account_record_id on old_bip_tags(tenant_record_id, account_record_id);

drop table if exists old_bip_fields;
create table old_bip_fields (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, name varchar(50) not null
, value varchar(255) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
create index old_bip_fields_tenant_account_record_id on old_bip_fields(tenant_record_id, account_record_id);
