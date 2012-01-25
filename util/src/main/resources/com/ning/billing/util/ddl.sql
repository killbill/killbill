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
DROP TABLE IF EXISTS tag_definitions;
CREATE TABLE tag_definitions (
  id char(36) NOT NULL,
  name varchar(20) NOT NULL,
  created_by varchar(50) NOT NULL,
  creation_date datetime NOT NULL,
  description varchar(200) NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX tag_definitions_name ON tag_definitions(name);

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
  id char(36) NOT NULL,
  tag_definition_name varchar(20) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  added_date datetime NOT NULL,
  added_by varchar(50) NOT NULL,
  PRIMARY KEY(id)
) ENGINE = innodb;
CREATE INDEX tags_by_object ON tags(object_id);
CREATE UNIQUE INDEX tags_unique ON tags(tag_definition_name, object_id);

DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    notification_id char(36) NOT NULL,
    created_dt datetime NOT NULL,
	notification_key varchar(256) NOT NULL,
    effective_dt datetime NOT NULL,
    processing_owner char(36) DEFAULT NULL,
    processing_available_dt datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX  `idx_comp_where` ON notifications (`effective_dt`,`processing_state`,`processing_owner`,`processing_available_dt`);
CREATE INDEX  `idx_update` ON notifications (`notification_id`,`processing_state`,`processing_owner`,`processing_available_dt`);
CREATE INDEX  `idx_update1` ON notifications (`notification_id`,`processing_owner`);
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
