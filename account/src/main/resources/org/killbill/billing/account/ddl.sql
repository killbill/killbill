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
CREATE UNIQUE INDEX accounts_id ON accounts(id);
CREATE UNIQUE INDEX accounts_external_key ON accounts(external_key, tenant_record_id);
CREATE INDEX accounts_parents ON accounts(parent_account_id);
CREATE INDEX accounts_tenant_record_id ON accounts(tenant_record_id);
CREATE INDEX accounts_email_tenant_record_id ON accounts(email, tenant_record_id);
CREATE INDEX accounts_company_name_tenant_record_id ON accounts(company_name, tenant_record_id);
CREATE INDEX accounts_name_tenant_record_id ON accounts(name, tenant_record_id);


DROP TABLE IF EXISTS account_history;
CREATE TABLE account_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    external_key varchar(255) NOT NULL,
    email varchar(128) DEFAULT NULL,
    name varchar(100) DEFAULT NULL,
    first_name_length int DEFAULT NULL,
    currency varchar(3) DEFAULT NULL,
    billing_cycle_day_local int DEFAULT NULL,
    parent_account_id varchar(36) DEFAULT NULL,
    is_payment_delegated_to_parent boolean default false,
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
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX account_history_target_record_id ON account_history(target_record_id);
CREATE INDEX account_history_tenant_record_id ON account_history(tenant_record_id);

DROP TABLE IF EXISTS account_emails;
CREATE TABLE account_emails (
    record_id serial unique,
    id varchar(36) NOT NULL,
    account_id varchar(36) NOT NULL,
    email varchar(128) NOT NULL,
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX account_email_id ON account_emails(id);
CREATE INDEX account_email_account_id_email ON account_emails(account_id, email);
CREATE INDEX account_emails_tenant_account_record_id ON account_emails(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS account_email_history;
CREATE TABLE account_email_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    account_id varchar(36) NOT NULL,
    email varchar(128) NOT NULL,
    is_active boolean default true,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX account_email_target_record_id ON account_email_history(target_record_id);
CREATE INDEX account_email_history_tenant_account_record_id ON account_email_history(tenant_record_id, account_record_id);
