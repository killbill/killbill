DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    id char(36) NOT NULL,
    key_name varchar(128) NOT NULL,
    PRIMARY KEY(id)
) ENGINE=innodb;
