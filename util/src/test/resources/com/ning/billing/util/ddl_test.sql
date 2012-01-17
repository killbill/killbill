DROP TABLE IF EXISTS dummy;
CREATE TABLE dummy (
    dummy_id char(36) NOT NULL,
    value varchar(256) NOT NULL,
    PRIMARY KEY(dummy_id)
) ENGINE = innodb;
