DROP TABLE IF EXISTS custom_fields;
CREATE TABLE custom_fields (
  id char(36) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  field_name varchar(30) NOT NULL,
  field_value varchar(255),
  created_by varchar(50) NOT NULL,
  created_date datetime NOT NULL,
  updated_by varchar(50) DEFAULT NULL,
  updated_date datetime DEFAULT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX custom_fields_object_id_object_type ON custom_fields(object_id, object_type);
CREATE UNIQUE INDEX custom_fields_unique ON custom_fields(object_id, object_type, field_name);

DROP TABLE IF EXISTS custom_field_history;
CREATE TABLE custom_field_history (
  history_id char(36) NOT NULL,
  id char(36) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  field_name varchar(30),
  field_value varchar(255),
  updated_by varchar(50) NOT NULL,
  date datetime NOT NULL,
  change_type char(6) NOT NULL
) ENGINE=innodb;
CREATE INDEX custom_field_history_object_id_object_type ON custom_fields(object_id, object_type);

DROP TABLE IF EXISTS tag_descriptions;
DROP TABLE IF EXISTS tag_definitions;
CREATE TABLE tag_definitions (
  id char(36) NOT NULL,
  name varchar(20) NOT NULL,
  description varchar(200) NOT NULL,
  created_by varchar(50) NOT NULL,
  created_date datetime NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX tag_definitions_name ON tag_definitions(name);

DROP TABLE IF EXISTS tag_definition_history;
CREATE TABLE tag_definition_history (
  id char(36) NOT NULL,
  name varchar(30) NOT NULL,
  created_by varchar(50),
  description varchar(200),
  change_type char(6) NOT NULL,
  updated_by varchar(50) NOT NULL,
  date datetime NOT NULL
) ENGINE=innodb;
CREATE INDEX tag_definition_history_id ON tag_definition_history(id);
CREATE INDEX tag_definition_history_name ON tag_definition_history(name);

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
  id char(36) NOT NULL,
  tag_definition_name varchar(20) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  created_by varchar(50) NOT NULL,
  created_date datetime NOT NULL,
  PRIMARY KEY(id)
) ENGINE = innodb;
CREATE INDEX tags_by_object ON tags(object_id);
CREATE UNIQUE INDEX tags_unique ON tags(tag_definition_name, object_id);

DROP TABLE IF EXISTS tag_history;
CREATE TABLE tag_history (
  id char(36) NULL,
  tag_definition_name varchar(20) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  change_type char(6) NOT NULL,
  updated_by varchar(50) NOT NULL,
  date datetime NOT NULL
) ENGINE = innodb;
CREATE INDEX tag_history_by_object ON tags(object_id);

DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    notification_id char(36) NOT NULL,
    created_dt datetime NOT NULL,
	notification_key varchar(256) NOT NULL,
    effective_dt datetime NOT NULL,
    queue_name char(64) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_dt datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX  `idx_comp_where` ON notifications (`effective_dt`, `queue_name`, `processing_state`,`processing_owner`,`processing_available_dt`);
CREATE INDEX  `idx_update` ON notifications (`processing_state`,`processing_owner`,`processing_available_dt`);
CREATE INDEX  `idx_get_ready` ON notifications (`effective_dt`,`created_dt`,`id`);

DROP TABLE IF EXISTS claimed_notifications;
CREATE TABLE claimed_notifications (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    sequence_id int(11) unsigned NOT NULL,
    owner_id varchar(64) NOT NULL,
    claimed_dt datetime NOT NULL,
    notification_id char(36) NOT NULL,
    PRIMARY KEY(id)
) ENGINE=innodb;

DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    table_name varchar(50) NOT NULL,
    record_id char(36) NOT NULL,
    change_type char(6) NOT NULL,
    change_date datetime NOT NULL,
    changed_by varchar(50) NOT NULL,
    reason_code varchar(20) DEFAULT NULL,
    comments varchar(255) DEFAULT NULL,
    PRIMARY KEY(id)
) ENGINE=innodb;

DROP TABLE IF EXISTS overdue_states;
CREATE TABLE overdue_states (
  id char(36) NOT NULL,
  state varchar(50) NOT NULL,
  created_date datetime NOT NULL
) ENGINE=innodb;
CREATE INDEX overdue_states_by_id ON overdue_states (id);

