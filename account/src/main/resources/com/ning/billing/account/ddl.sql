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
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX accounts_external_key ON accounts(external_key);
CREATE UNIQUE INDEX accounts_email ON accounts(email);

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
CREATE UNIQUE INDEX custom_fields_unique ON custom_fields(object_id, object_type, field_name);

DROP TABLE IF EXISTS tag_descriptions;
CREATE TABLE tag_descriptions (
  id char(36) NOT NULL,
  name varchar(20) NOT NULL,
  created_by varchar(50) NOT NULL,
  creation_date datetime NOT NULL,
  description varchar(200) NOT NULL,
  generate_invoice boolean DEFAULT false,
  process_payment boolean DEFAULT false,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX tag_descriptions_name ON tag_descriptions(name);

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
  id char(36) NOT NULL,
  tag_description_id char(36) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  date_added datetime NOT NULL,
  added_by varchar(50) NOT NULL,
  PRIMARY KEY(id)
) ENGINE = innodb;
CREATE INDEX tags_by_object ON tags(object_id);
CREATE UNIQUE INDEX tags_unique ON tags(tag_description_id, object_id);