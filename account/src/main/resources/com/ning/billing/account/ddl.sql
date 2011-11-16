DROP TABLE IF EXISTS accounts;
CREATE TABLE accounts (
    id char(36) NOT NULL,
    key_name varchar(128) NOT NULL,

    PRIMARY KEY(id)
) ENGINE=innodb;

DROP TABLE IF EXISTS custom_fields;
CREATE TABLE custom_fields (
  id char(36) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  field_name varchar(30) NOT NULL,
  field_value varchar(255) NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;

CREATE INDEX custom_fields_object_id_object_type ON custom_fields(object_id, object_type);