/*! SET storage_engine=INNODB */;


DROP TABLE IF EXISTS payment_attempts;
CREATE TABLE payment_attempts (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    payment_external_key char(128) NOT NULL,
    direct_transaction_id char(36),
    transaction_external_key char(128) NOT NULL,
    state_name varchar(32) NOT NULL,
    operation_name varchar(32) NOT NULL,
    plugin_name varchar(50),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX payment_attempts_id ON payment_attempts(id);
CREATE INDEX payment_attempts_payment ON payment_attempts(direct_transaction_id);
CREATE INDEX payment_attempts_payment_key ON payment_attempts(payment_external_key);
CREATE INDEX payment_attempts_payment_transaction_key ON payment_attempts(transaction_external_key);
CREATE INDEX payment_attempts_tenant_account_record_id ON payment_attempts(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_attempt_history;
CREATE TABLE payment_attempt_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    target_record_id int(11) unsigned NOT NULL,
    payment_external_key char(128) NOT NULL,
    direct_transaction_id char(36),
    transaction_external_key char(128) NOT NULL,
    state_name varchar(32) NOT NULL,
    operation_name varchar(32) NOT NULL,
    plugin_name varchar(50),
    change_type char(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX payment_attempt_history_target_record_id ON payment_attempt_history(target_record_id);
CREATE INDEX payment_attempt_history_tenant_account_record_id ON payment_attempt_history(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_methods;
CREATE TABLE payment_methods (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) NOT NULL,
    plugin_name varchar(50) DEFAULT NULL,
    is_active bool DEFAULT true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX payment_methods_id ON payment_methods(id);
CREATE INDEX payment_methods_active_accnt ON payment_methods(is_active, account_id);
CREATE INDEX payment_methods_tenant_account_record_id ON payment_methods(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_method_history;
CREATE TABLE payment_method_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    target_record_id int(11) unsigned NOT NULL,
    account_id char(36) NOT NULL,
    plugin_name varchar(50) DEFAULT NULL,
    is_active bool DEFAULT true,
    change_type char(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX payment_method_history_target_record_id ON payment_method_history(target_record_id);
CREATE INDEX payment_method_history_tenant_account_record_id ON payment_method_history(tenant_record_id, account_record_id);


DROP TABLE IF EXISTS direct_payments;
CREATE TABLE direct_payments (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) NOT NULL,
    payment_method_id char(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    current_state_name varchar(255),
    ext_first_payment_ref_id varchar(128),
    ext_second_payment_ref_id varchar(128),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX direct_payments_id ON direct_payments(id);
CREATE UNIQUE INDEX direct_payments_key ON direct_payments(external_key);
CREATE INDEX direct_payments_accnt ON direct_payments(account_id);
CREATE INDEX direct_payments_tenant_account_record_id ON direct_payments(tenant_record_id, account_record_id);


DROP TABLE IF EXISTS direct_payment_history;
CREATE TABLE direct_payment_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    target_record_id int(11) unsigned NOT NULL,
    account_id char(36) NOT NULL,
    payment_method_id char(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    current_state_name varchar(255),
    ext_first_payment_ref_id varchar(128),
    ext_second_payment_ref_id varchar(128),
    change_type char(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX direct_payment_history_target_record_id ON direct_payment_history(target_record_id);
CREATE INDEX direct_payment_history_tenant_account_record_id ON direct_payment_history(tenant_record_id, account_record_id);


DROP TABLE IF EXISTS direct_transactions;
CREATE TABLE direct_transactions (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    transaction_external_key varchar(255) NOT NULL,
    transaction_type varchar(32) NOT NULL,
    effective_date datetime NOT NULL,
    payment_status varchar(50),
    amount numeric(15,9),
    currency char(3),
    processed_amount numeric(15,9),
    processed_currency char(3),
    direct_payment_id char(36) NOT NULL,
    gateway_error_code varchar(32),
    gateway_error_msg varchar(256),
    ext_first_payment_ref_id varchar(128),
    ext_second_payment_ref_id varchar(128),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX direct_transactions_id ON direct_transactions(id);
CREATE INDEX direct_transactions_direct_id ON direct_transactions(direct_payment_id);
CREATE UNIQUE INDEX direct_transactions_direct_key ON direct_transactions(transaction_external_key, payment_status);
CREATE INDEX direct_transactions_tenant_account_record_id ON direct_transactions(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS direct_transaction_history;
CREATE TABLE direct_transaction_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    transaction_external_key varchar(255) NOT NULL,
    target_record_id int(11) unsigned NOT NULL,
    transaction_type varchar(32) NOT NULL,
    effective_date datetime NOT NULL,
    payment_status varchar(50),
    amount numeric(15,9),
    currency char(3),
    processed_amount numeric(15,9),
    processed_currency char(3),
    direct_payment_id char(36) NOT NULL,
    gateway_error_code varchar(32),
    gateway_error_msg varchar(256),
    ext_first_payment_ref_id varchar(128),
    ext_second_payment_ref_id varchar(128),
    change_type char(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned DEFAULT NULL,
    tenant_record_id int(11) unsigned DEFAULT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX direct_transaction_history_target_record_id ON direct_transaction_history(target_record_id);
CREATE INDEX direct_transaction_history_tenant_account_record_id ON direct_transaction_history(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS payment_plugin_properties;
CREATE TABLE payment_plugin_properties (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    payment_external_key varchar(255),
    transaction_external_key varchar(255),
    account_id char(36) NOT NULL,
    plugin_name varchar(50) DEFAULT NULL,
    prop_key varchar(255),
    prop_value varchar(255),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX payment_plugin_properties_ext ON payment_plugin_properties(transaction_external_key);

/*  PaymentControlPlugin lives  here until this becomes a first class citizen plugin */
DROP TABLE IF EXISTS _invoice_payment_control_plugin_auto_pay_off;
CREATE TABLE _invoice_payment_control_plugin_auto_pay_off (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    payment_external_key varchar(255),
    transaction_external_key varchar(255),
    account_id char(36) NOT NULL,
    plugin_name varchar(50) DEFAULT NULL,
    payment_id char(36),
    payment_method_id char(36) NOT NULL,
    amount numeric(15,9),
    currency char(3),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    PRIMARY KEY (record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX _invoice_payment_control_plugin_auto_pay_off_account ON _invoice_payment_control_plugin_auto_pay_off(account_id);
