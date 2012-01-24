DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    id char(36) NOT NULL,
    external_key varchar(128) NULL,
    email varchar(50) NOT NULL,
    name varchar(100) NOT NULL,
    first_name_length int NOT NULL,
    phone varchar(13) DEFAULT NULL,
    currency char(3) DEFAULT NULL,
    billing_cycle_day int DEFAULT NULL,
    payment_provider_name varchar(20) DEFAULT NULL,
    created_dt datetime,
    updated_dt datetime,
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX accounts_external_key ON accounts(external_key);
CREATE UNIQUE INDEX accounts_email ON accounts(email);