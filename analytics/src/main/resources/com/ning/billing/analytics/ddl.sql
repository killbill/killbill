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
, prev_product_name varchar(32) default null
, prev_product_type varchar(32) default null
, prev_product_category varchar(32) default null
, prev_slug varchar(50) default null
, prev_phase varchar(32) default null
, prev_billing_period varchar(32) default null
, prev_price numeric(10, 4) default 0
, prev_price_list varchar(32) default null
, prev_mrr numeric(10, 4) default 0
, prev_currency varchar(32) default null
, prev_start_date bigint default null
, prev_state varchar(32) default null
, next_product_name varchar(32) default null
, next_product_type varchar(32) default null
, next_product_category varchar(32) default null
, next_slug varchar(50) default null
, next_phase varchar(32) default null
, next_billing_period varchar(32) default null
, next_price numeric(10, 4) default 0
, next_price_list varchar(32) default null
, next_mrr numeric(10, 4) default 0
, next_currency varchar(32) default null
, next_start_date bigint default null
, next_state varchar(32) default null
, primary key(record_id)
) engine=innodb comment 'Business Subscription Transitions, track bundles lifecycle';
create index bst_key_index on bst (external_key, requested_timestamp asc);

drop table if exists bac;
create table bac (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null
, name varchar(100) not null
, created_date bigint not null
, updated_date bigint not null
, balance numeric(10, 4) default 0
, last_invoice_date bigint default null
, total_invoice_balance numeric(10, 4) default 0
, last_payment_status varchar(100) default null
, payment_method varchar(100) default null
, credit_card_type varchar(32) default null
, billing_address_country varchar(100) default null
, currency char(3) default null
, primary key(record_id)
) engine=innodb comment 'Business ACcounts, keep a record of all accounts';
create unique index bac_key_index on bac (account_key);

drop table if exists bin;
create table bin (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, invoice_number bigint default null
, created_date bigint not null
, updated_date bigint not null
, account_id char(36) not null
, account_key varchar(50) not null
, invoice_date bigint not null
, target_date bigint not null
, currency char(3) not null
, balance numeric(10, 4) default 0 comment 'amount_charged - amount_paid - amount_credited'
, amount_paid numeric(10, 4) default 0 comment 'Sums of the successful payments made for this invoice minus the refunds associated with this invoice'
, amount_charged numeric(10, 4) default 0 comment 'Sums of the invoice items amount'
, amount_credited numeric(10, 4) default 0 comment 'Sums of the credit items'
, primary key(record_id)
) engine=innodb comment 'Business INvoices, keep a record of generated invoices';
create unique index bin_key_index on bin (invoice_id);

drop table if exists bii;
create table bii (
  record_id int(11) unsigned not null auto_increment
, item_id char(36) not null
, created_date bigint not null
, updated_date bigint not null
, invoice_id char(36) not null
, item_type char(20) not null comment 'e.g. FIXED or RECURRING'
, external_key varchar(50) not null comment 'Bundle external key'
, product_name varchar(32) default null
, product_type varchar(32) default null
, product_category varchar(32) default null
, slug varchar(50) default null comment 'foo'
, phase varchar(32) default null
, billing_period varchar(32) default null
, start_date bigint default null
, end_date bigint default null
, amount numeric(10, 4) default 0
, currency char(3) default null
, primary key(record_id)
) engine=innodb comment 'Business Invoice Items, keep a record of all invoice items';
create unique index bii_key_index on bii (item_id);

drop table if exists bip;
create table bip (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, created_date bigint not null
, updated_date bigint not null
, attempt_id char(36) not null
, account_key varchar(50) not null comment 'Account external key'
, invoice_id char(36) not null
, effective_date bigint default null
, amount numeric(10, 4) default 0
, currency char(3) default null
, payment_error varchar(256) default null
, processing_status varchar(50) default null
, requested_amount numeric(10, 4) default 0
, plugin_name varchar(20) default null
, payment_type varchar(20) default null
, payment_method varchar(20) default null
, card_type varchar(20) default null
, card_country varchar(20) default null
, primary key(record_id)
) engine=innodb comment 'Business Invoice Payments, track all payment attempts';
create unique index bip_key_index on bip (attempt_id);

drop table if exists bos;
create table bos (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, status varchar(50) not null
, start_date bigint default null
, end_date bigint default null
, primary key(record_id)
) engine=innodb comment 'Business Overdue Status, historical bundles overdue status';
create unique index bos_key_index on bos (external_key, status);

drop table if exists bac_tags;
create table bac_tags (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null comment 'Account external key'
, name varchar(20) not null
, primary key(record_id)
) engine=innodb comment 'Tags associated to accounts';

drop table if exists bac_fields;
create table bac_fields (
  record_id int(11) unsigned not null auto_increment
, account_id char(36) not null
, account_key varchar(50) not null comment 'Account external key'
, name varchar(30) not null
, value varchar(255) default null
, primary key(record_id)
) engine=innodb comment 'Custom fields associated to accounts';

drop table if exists bst_tags;
create table bst_tags (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, name varchar(20) not null
, primary key(record_id)
) engine=innodb comment 'Tags associated to bundles';

drop table if exists bst_fields;
create table bst_fields (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) not null
, external_key varchar(50) not null comment 'Bundle external key'
, account_key varchar(50) not null comment 'Account external key'
, name varchar(30) not null
, value varchar(255) default null
, primary key(record_id)
) engine=innodb comment 'Custom fields associated to bundles';

drop table if exists bin_tags;
create table bin_tags (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, name varchar(20) not null
, primary key(record_id)
) engine=innodb comment 'Tags associated to invoices';

drop table if exists bin_fields;
create table bin_fields (
  record_id int(11) unsigned not null auto_increment
, invoice_id char(36) not null
, name varchar(30) not null
, value varchar(255) default null
, primary key(record_id)
) engine=innodb comment 'Custom fields associated to invoices';

drop table if exists bip_tags;
create table bip_tags (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, name varchar(20) not null
, primary key(record_id)
) engine=innodb comment 'Tags associated to payments';

drop table if exists bip_fields;
create table bip_fields (
  record_id int(11) unsigned not null auto_increment
, payment_id char(36) not null
, name varchar(30) not null
, value varchar(255) default null
, primary key(record_id)
) engine=innodb comment 'Custom fields associated to payments';
