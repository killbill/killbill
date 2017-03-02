/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS payment_attempts;
CREATE TABLE payment_attempts (
    record_id serial unique,
    id varchar(36) NOT NULL,
    account_id varchar(36) NOT NULL,
    payment_method_id varchar(36) DEFAULT NULL,
    payment_external_key varchar(255) NOT NULL,
    transaction_id varchar(36),
    transaction_external_key varchar(255) NOT NULL,
    transaction_type varchar(32) NOT NULL,
    state_name varchar(32) NOT NULL,
    amount numeric(15,9),
    currency varchar(3),
    plugin_name varchar(1024) NOT NULL,
    plugin_properties mediumblob,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX payment_attempts_id ON payment_attempts(id);
CREATE INDEX payment_attempts_payment ON payment_attempts(transaction_id);
CREATE INDEX payment_attempts_payment_key ON payment_attempts(payment_external_key);
CREATE INDEX payment_attempts_payment_state ON payment_attempts(state_name);
CREATE INDEX payment_attempts_payment_transaction_key ON payment_attempts(transaction_external_key);
CREATE INDEX payment_attempts_tenant_account_record_id ON payment_attempts(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_attempt_history;
CREATE TABLE payment_attempt_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    account_id varchar(36) NOT NULL,
    payment_method_id varchar(36) DEFAULT NULL,
    payment_external_key varchar(255) NOT NULL,
    transaction_id varchar(36),
    transaction_external_key varchar(255) NOT NULL,
    transaction_type varchar(32) NOT NULL,
    state_name varchar(32) NOT NULL,
    amount numeric(15,9),
    currency varchar(3),
    plugin_name varchar(1024) NOT NULL,
    plugin_properties mediumblob,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX payment_attempt_history_target_record_id ON payment_attempt_history(target_record_id);
CREATE INDEX payment_attempt_history_tenant_account_record_id ON payment_attempt_history(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_methods;
CREATE TABLE payment_methods (
    record_id serial unique,
    id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    account_id varchar(36) NOT NULL,
    plugin_name varchar(50) NOT NULL,
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX payment_methods_id ON payment_methods(id);
CREATE UNIQUE INDEX payment_methods_external_key ON payment_methods(external_key, tenant_record_id);
CREATE INDEX payment_methods_plugin_name ON payment_methods(plugin_name);
CREATE INDEX payment_methods_tenant_account_record_id ON payment_methods(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_method_history;
CREATE TABLE payment_method_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    account_id varchar(36) NOT NULL,
    plugin_name varchar(50) NOT NULL,
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
CREATE INDEX payment_method_history_target_record_id ON payment_method_history(target_record_id);
CREATE INDEX payment_method_history_tenant_account_record_id ON payment_method_history(tenant_record_id, account_record_id);


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
CREATE UNIQUE INDEX payments_id ON payments(id);
CREATE UNIQUE INDEX payments_key ON payments(external_key, tenant_record_id);
CREATE INDEX payments_accnt ON payments(account_id);
CREATE INDEX payments_tenant_account_record_id ON payments(tenant_record_id, account_record_id);
CREATE INDEX payments_tenant_record_id_state_name ON payments(tenant_record_id, state_name);


DROP TABLE IF EXISTS payment_history;
CREATE TABLE payment_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    account_id varchar(36) NOT NULL,
    payment_method_id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    state_name varchar(64) DEFAULT NULL,
    last_success_state_name varchar(64) DEFAULT NULL,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX payment_history_target_record_id ON payment_history(target_record_id);
CREATE INDEX payment_history_tenant_account_record_id ON payment_history(tenant_record_id, account_record_id);


DROP TABLE IF EXISTS payment_transactions;
CREATE TABLE payment_transactions (
    record_id serial unique,
    id varchar(36) NOT NULL,
    attempt_id varchar(36) DEFAULT NULL,
    transaction_external_key varchar(255) NOT NULL,
    transaction_type varchar(32) NOT NULL,
    effective_date datetime NOT NULL,
    transaction_status varchar(50) NOT NULL,
    amount numeric(15,9),
    currency varchar(3),
    processed_amount numeric(15,9),
    processed_currency varchar(3),
    payment_id varchar(36) NOT NULL,
    gateway_error_code varchar(32),
    gateway_error_msg text,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX transactions_id ON payment_transactions(id);
CREATE INDEX transactions_payment_id ON payment_transactions(payment_id);
CREATE INDEX transactions_key ON payment_transactions(transaction_external_key);
CREATE INDEX transactions_status ON payment_transactions(transaction_status);
CREATE INDEX transactions_tenant_account_record_id ON payment_transactions(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_transaction_history;
CREATE TABLE payment_transaction_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    attempt_id varchar(36) DEFAULT NULL,
    transaction_external_key varchar(255) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    transaction_type varchar(32) NOT NULL,
    effective_date datetime NOT NULL,
    transaction_status varchar(50) NOT NULL,
    amount numeric(15,9),
    currency varchar(3),
    processed_amount numeric(15,9),
    processed_currency varchar(3),
    payment_id varchar(36) NOT NULL,
    gateway_error_code varchar(32),
    gateway_error_msg text,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX transaction_history_target_record_id ON payment_transaction_history(target_record_id);
CREATE INDEX transaction_history_tenant_account_record_id ON payment_transaction_history(tenant_record_id, account_record_id);


/*  PaymentControlPlugin lives  here until this becomes a first class citizen plugin */
DROP TABLE IF EXISTS _invoice_payment_control_plugin_auto_pay_off;
CREATE TABLE _invoice_payment_control_plugin_auto_pay_off (
    record_id serial unique,
    attempt_id varchar(36) NOT NULL,
    payment_external_key varchar(255) NOT NULL,
    transaction_external_key varchar(255) NOT NULL,
    account_id varchar(36) NOT NULL,
    plugin_name varchar(50) NOT NULL,
    payment_id varchar(36),
    payment_method_id varchar(36) NOT NULL,
    amount numeric(15,9),
    currency varchar(3),
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX _invoice_payment_control_plugin_auto_pay_off_account ON _invoice_payment_control_plugin_auto_pay_off(account_id);
