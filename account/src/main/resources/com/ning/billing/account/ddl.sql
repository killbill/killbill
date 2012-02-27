DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
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
    created_dt datetime,
    updated_dt datetime,
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX accounts_external_key ON accounts(external_key);
CREATE UNIQUE INDEX accounts_email ON accounts(email);

DROP TABLE IF EXISTS account_history;
CREATE TABLE account_history (
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
    date datetime
) ENGINE=innodb;
CREATE INDEX account_id ON account_history(id);

CREATE TRIGGER store_account_history_on_insert AFTER INSERT ON accounts
    FOR EACH ROW
        INSERT INTO account_history (id, external_key, email, name, first_name_length, currency,
                                    billing_cycle_day, payment_provider_name, time_zone, locale, 
                                    address1, address2, company_name, city, state_or_province, 
                                    country, postal_code, phone, date)
        VALUES (NEW.id, NEW.external_key, NEW.email, NEW.name, NEW.first_name_length, NEW.currency,
                NEW.billing_cycle_day, NEW.payment_provider_name, NEW.time_zone, NEW.locale, 
                NEW.address1, NEW.address2, NEW.company_name, NEW.city, NEW.state_or_province, 
                NEW.country, NEW.postal_code, NEW.phone, NEW.created_dt);

CREATE TRIGGER store_account_history_on_update AFTER UPDATE ON accounts
    FOR EACH ROW
        INSERT INTO account_history (id, external_key, email, name, first_name_length, currency,
                                    billing_cycle_day, payment_provider_name, time_zone, locale, 
                                    address1, address2, company_name, city, state_or_province, 
                                    country, postal_code, phone, date)
        VALUES (NEW.id, NEW.external_key, NEW.email, NEW.name, NEW.first_name_length, NEW.currency,
                NEW.billing_cycle_day, NEW.payment_provider_name, NEW.time_zone, NEW.locale, 
                NEW.address1, NEW.address2, NEW.company_name, NEW.city, NEW.state_or_province, 
                NEW.country, NEW.postal_code, NEW.phone, NEW.updated_dt);
