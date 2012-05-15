DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    external_key varchar(128) NULL,
    email varchar(50) NOT NULL,
    name varchar(100) NOT NULL,
    first_name_length int NOT NULL,
    currency char(3) DEFAULT NULL,
    billing_cycle_day int DEFAULT NULL,
    payment_provider_name varchar(20) DEFAULT NULL,
    time_zone varchar(50) DEFAULT NULL,
    locale varchar(5) DEFAULT NULL,
    address1 varchar(100) DEFAULT NULL,
    address2 varchar(100) DEFAULT NULL,
    company_name varchar(50) DEFAULT NULL,
    city varchar(50) DEFAULT NULL,
    state_or_province varchar(50) DEFAULT NULL,
    country varchar(50) DEFAULT NULL,
    postal_code varchar(11) DEFAULT NULL,
    phone varchar(25) DEFAULT NULL,
    migrated bool DEFAULT false,
    is_notified_for_invoices boolean NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX accounts_id ON accounts(id);
CREATE UNIQUE INDEX accounts_external_key ON accounts(external_key);
CREATE UNIQUE INDEX accounts_email ON accounts(email);

DROP TABLE IF EXISTS account_history;
CREATE TABLE account_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    external_key varchar(128) NULL,
    email varchar(50) NOT NULL,
    name varchar(100) NOT NULL,
    first_name_length int NOT NULL,
    currency char(3) DEFAULT NULL,
    billing_cycle_day int DEFAULT NULL,
    payment_provider_name varchar(20) DEFAULT NULL,
    time_zone varchar(50) DEFAULT NULL,
    locale varchar(5) DEFAULT NULL,
    address1 varchar(100) DEFAULT NULL,
    address2 varchar(100) DEFAULT NULL,
    company_name varchar(50) DEFAULT NULL,
    city varchar(50) DEFAULT NULL,
    state_or_province varchar(50) DEFAULT NULL,
    country varchar(50) DEFAULT NULL,
    postal_code varchar(11) DEFAULT NULL,
    phone varchar(25) DEFAULT NULL,
    migrated bool DEFAULT false,
    is_notified_for_invoices boolean NOT NULL,
    change_type char(6) NOT NULL,
    updated_by varchar(50) NOT NULL,
    date datetime NOT NULL,
    PRIMARY KEY(history_record_id)
) ENGINE=innodb;
CREATE INDEX account_history_record_id ON account_history(record_id);

DROP TABLE IF EXISTS account_emails;
CREATE TABLE account_emails (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    account_id char(36) NOT NULL,
    email varchar(50) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX account_email_id ON account_emails(id);
CREATE INDEX account_email_account_id ON account_emails(account_id);
CREATE UNIQUE INDEX account_email_account_id_email ON account_emails(account_id, email);

DROP TABLE IF EXISTS account_email_history;
CREATE TABLE account_email_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    account_id char(36) NOT NULL,
    email varchar(50) NOT NULL,
    change_type char(6) NOT NULL,
    updated_by varchar(50) NOT NULL,
    date datetime NOT NULL,
    PRIMARY KEY(history_record_id)
) ENGINE=innodb;
CREATE INDEX account_email_record_id ON account_email_history(record_id);