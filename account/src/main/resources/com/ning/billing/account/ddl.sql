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
    city varchar(50) DEFAULT NULL,
    state_or_province varchar(50) DEFAULT NULL,
    country varchar(50) DEFAULT NULL,
    postal_code varchar(11) DEFAULT NULL,
    phone varchar(13) DEFAULT NULL,
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX accounts_external_key ON accounts(external_key);
CREATE UNIQUE INDEX accounts_email ON accounts(email);