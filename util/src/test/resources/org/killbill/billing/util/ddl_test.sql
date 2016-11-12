/*! SET default_storage_engine=INNODB */;

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
