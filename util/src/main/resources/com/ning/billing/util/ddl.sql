DROP TABLE IF EXISTS custom_fields;
CREATE TABLE custom_fields (
  id char(36) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  field_name varchar(30) NOT NULL,
  field_value varchar(255),
  created_date datetime NOT NULL,
  updated_date datetime NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX custom_fields_object_id_object_type ON custom_fields(object_id, object_type);
CREATE UNIQUE INDEX custom_fields_unique ON custom_fields(object_id, object_type, field_name);

DROP TABLE IF EXISTS custom_field_history;
CREATE TABLE custom_field_history (
  id char(36) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  field_name varchar(30),
  field_value varchar(255),
  date datetime NOT NULL,
  change_type char(6) NOT NULL
) ENGINE=innodb;
CREATE INDEX custom_field_history_object_id_object_type ON custom_fields(object_id, object_type);

CREATE TRIGGER store_custom_field_history_on_insert AFTER INSERT ON custom_fields
    FOR EACH ROW
        INSERT INTO custom_field_history (id, object_id, object_type, field_name, field_value, date, change_type)
        VALUES (NEW.id, NEW.object_id, NEW.object_type, NEW.field_name, NEW.field_value, NOW(), 'CREATE');

CREATE TRIGGER store_custom_field_history_on_update AFTER UPDATE ON custom_fields
    FOR EACH ROW
        INSERT INTO custom_field_history (id, object_id, object_type, field_name, field_value, date, change_type)
        VALUES (NEW.id, NEW.object_id, NEW.object_type, NEW.field_name, NEW.field_value, NOW(), 'UPDATE');

CREATE TRIGGER store_custom_field_history_on_delete BEFORE DELETE ON custom_fields
    FOR EACH ROW
        INSERT INTO custom_field_history (id, object_id, object_type, field_name, field_value, date, change_type)
        VALUES (OLD.id, OLD.object_id, OLD.object_type, NULL, NULL, NOW(), 'DELETE');

DROP TABLE IF EXISTS tag_descriptions;
DROP TABLE IF EXISTS tag_definitions;
CREATE TABLE tag_definitions (
  id char(36) NOT NULL,
  name varchar(20) NOT NULL,
  created_by varchar(50) NOT NULL,
  description varchar(200) NOT NULL,
  created_date datetime NOT NULL,
  updated_date datetime NOT NULL,
  PRIMARY KEY(id)
) ENGINE=innodb;
CREATE UNIQUE INDEX tag_definitions_name ON tag_definitions(name);

DROP TABLE IF EXISTS tag_definition_history;
CREATE TABLE tag_definition_history (
  id char(36) NOT NULL,
  name varchar(30) NOT NULL,
  created_by varchar(50),
  description varchar(200),
  date datetime NOT NULL,
  change_type char(6) NOT NULL
) ENGINE=innodb;
CREATE INDEX tag_definition_history_id ON tag_definition_history(id);
CREATE INDEX tag_definition_history_name ON tag_definition_history(name);

CREATE TRIGGER tag_definition_history_after_insert AFTER INSERT ON tag_definition_history
    FOR EACH ROW
        INSERT INTO tag_definition_history (id, name, created_by, description, date, change_type)
        VALUES (NEW.id, NEW.name, NEW.created_by, NEW.description, NOW(), 'CREATE');

CREATE TRIGGER tag_definition_history_after_update AFTER UPDATE ON tag_definition_history
    FOR EACH ROW
        INSERT INTO tag_definition_history (id, name, created_by, description, date, change_type)
        VALUES (NEW.id, NEW.name, NEW.created_by, NEW.description, NOW(), 'UPDATE');

CREATE TRIGGER tag_definition_history_before_delete BEFORE DELETE ON tag_definition_history
    FOR EACH ROW
        INSERT INTO tag_definition_history (id, name, created_by, description, date, change_type)
        VALUES (OLD.id, OLD.name, NULL, NULL, NOW(), 'DELETE');

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

DROP TABLE IF EXISTS tag_history;
CREATE TABLE tag_history (
  id char(36) NULL,
  tag_definition_name varchar(20) NOT NULL,
  object_id char(36) NOT NULL,
  object_type varchar(30) NOT NULL,
  date datetime NOT NULL,
  change_type char(6) NOT NULL
) ENGINE = innodb;
CREATE INDEX tag_history_by_object ON tags(object_id);

CREATE TRIGGER tag_history_after_insert AFTER INSERT ON tag_history
    FOR EACH ROW
        INSERT INTO tag_history (id, tag_definition_name, object_id, object_type, date, change_type)
        VALUES (NEW.id, NEW.tag_definition_name, NEW.object_id, NEW.object_type, NOW(), 'CREATE');

CREATE TRIGGER tag_history_after_update AFTER UPDATE ON tag_history
    FOR EACH ROW
        INSERT INTO tag_history (id, tag_definition_name, object_id, object_type, date, change_type)
        VALUES (NEW.id, NEW.tag_definition_name, NEW.object_id, NEW.object_type, NOW(), 'UPDATE');

CREATE TRIGGER tag_history_before_delete BEFORE DELETE ON tag_history
    FOR EACH ROW
        INSERT INTO tag_history (id, tag_definition_name, object_id, object_type, date, change_type)
        VALUES (OLD.id, OLD.tag_definition_name, OLD.object_id, OLD.object_type, NOW(), 'DELETE');

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