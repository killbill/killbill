/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    record_id serial unique,
    id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    email varchar(128) DEFAULT NULL,
    name varchar(100) DEFAULT NULL,
    first_name_length int DEFAULT NULL,
    currency varchar(3) DEFAULT NULL,
    billing_cycle_day_local int DEFAULT NULL,
    parent_account_id varchar(36) DEFAULT NULL,
    is_payment_delegated_to_parent boolean DEFAULT FALSE,
    payment_method_id varchar(36) DEFAULT NULL,
    reference_time datetime NOT NULL,
    time_zone varchar(50) NOT NULL,
    locale varchar(5) DEFAULT NULL,
    address1 varchar(100) DEFAULT NULL,
    address2 varchar(100) DEFAULT NULL,
    company_name varchar(50) DEFAULT NULL,
    city varchar(50) DEFAULT NULL,
    state_or_province varchar(50) DEFAULT NULL,
    country varchar(50) DEFAULT NULL,
    postal_code varchar(16) DEFAULT NULL,
    phone varchar(25) DEFAULT NULL,
    notes varchar(4096) DEFAULT NULL,
    migrated boolean default false,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;

DROP TABLE IF EXISTS tenants;
CREATE TABLE tenants (
    record_id serial unique,
    id varchar(36) NOT NULL,
    external_key varchar(255) NULL,
    api_key varchar(128) NULL,
    api_secret varchar(128) NULL,
    api_salt varchar(128) NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;

DROP TABLE IF EXISTS bundles;
CREATE TABLE bundles (
    record_id serial unique,
    id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    account_id varchar(36) NOT NULL,
    last_sys_update_date datetime,
    original_created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX bundles_external_key ON bundles(external_key, tenant_record_id);
DROP TABLE IF EXISTS subscriptions;
CREATE TABLE subscriptions (
    record_id serial unique,
    id varchar(36) NOT NULL,
    bundle_id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    category varchar(32) NOT NULL,
    start_date datetime NOT NULL,
    bundle_start_date datetime NOT NULL,
    charged_through_date datetime DEFAULT NULL,
    migrated bool NOT NULL default FALSE,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;

DROP TABLE IF EXISTS invoices;
CREATE TABLE invoices (
    record_id serial unique,
    id varchar(36) NOT NULL,
    account_id varchar(36) NOT NULL,
    invoice_date date NOT NULL,
    target_date date,
    currency varchar(3) NOT NULL,
    status varchar(15) NOT NULL DEFAULT 'COMMITTED',
    migrated bool NOT NULL,
    parent_invoice bool NOT NULL DEFAULT FALSE,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;

DROP TABLE IF EXISTS payments;
CREATE TABLE payments (
    record_id serial unique,
    id varchar(36) NOT NULL,
    account_id varchar(36) NOT NULL,
    payment_method_id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    state_name varchar(64) DEFAULT NULL,
    last_success_state_name varchar(64) DEFAULT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;

DROP TABLE IF EXISTS dummy;
CREATE TABLE dummy (
    dummy_id varchar(36) NOT NULL,
    value varchar(256) NOT NULL,
    PRIMARY KEY(dummy_id)
);

DROP TABLE IF EXISTS dummy2;
CREATE TABLE dummy2 (
    id serial unique,
    dummy_id varchar(36) NOT NULL,
    PRIMARY KEY(id)
);

DROP TABLE IF EXISTS validation_test;
CREATE TABLE validation_test (
    column1 varchar(25),
    column2 char(2) NOT NULL,
    column3 numeric(10,4),
    column4 datetime
);

DROP TABLE IF EXISTS kombucha;
CREATE TABLE kombucha (
    record_id serial unique,
    id varchar(36) NOT NULL,
    tea varchar(50) NOT NULL,
    mushroom varchar(50) NOT NULL,
    sugar varchar(50) NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
);

DROP TABLE IF EXISTS full_of_dates;
CREATE TABLE full_of_dates (
    record_id serial unique,
    date1 date default NULL,
    datetime1 datetime default NULL,
    timestamp1 timestamp,
    PRIMARY KEY(record_id)
);
