DROP TABLE IF EXISTS dummy;
CREATE TABLE dummy (
    dummy_id char(36) NOT NULL,
    value varchar(256) NOT NULL,
    PRIMARY KEY(dummy_id)
) ENGINE = innodb;

DROP TABLE IF EXISTS dummy2;
CREATE TABLE dummy2 (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    dummy_id char(36) NOT NULL,
    PRIMARY KEY(id)
) ENGINE = innodb;

DROP TABLE IF EXISTS validation_test;
CREATE TABLE validation_test (
    column1 varchar(25),
    column2 char(2) NOT NULL,
    column3 numeric(10,4),
    column4 datetime
) ENGINE = innodb;
