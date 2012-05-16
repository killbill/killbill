DROP TABLE IF EXISTS custom_fields;
CREATE TABLE custom_fields (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    object_id char(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    field_name varchar(30) NOT NULL,
    field_value varchar(255),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) DEFAULT NULL,
    updated_date datetime DEFAULT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX custom_fields_id ON custom_fields(id);
CREATE INDEX custom_fields_object_id_object_type ON custom_fields(object_id, object_type);
CREATE UNIQUE INDEX custom_fields_unique ON custom_fields(object_id, object_type, field_name);

DROP TABLE IF EXISTS custom_field_history;
CREATE TABLE custom_field_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    object_id char(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    field_name varchar(30),
    field_value varchar(255),
    updated_by varchar(50) NOT NULL,
    date datetime NOT NULL,
    change_type char(6) NOT NULL,
    PRIMARY KEY(history_record_id)
) ENGINE=innodb;
CREATE INDEX custom_field_history_record_id ON custom_field_history(record_id);
CREATE INDEX custom_field_history_object_id_object_type ON custom_fields(object_id, object_type);

DROP TABLE IF EXISTS tag_descriptions;
DROP TABLE IF EXISTS tag_definitions;
CREATE TABLE tag_definitions (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    name varchar(20) NOT NULL,
    description varchar(200) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX tag_definitions_id ON tag_definitions(id);
CREATE UNIQUE INDEX tag_definitions_name ON tag_definitions(name);

DROP TABLE IF EXISTS tag_definition_history;
CREATE TABLE tag_definition_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    name varchar(30) NOT NULL,
    created_by varchar(50),
    description varchar(200),
    change_type char(6) NOT NULL,
    updated_by varchar(50) NOT NULL,
    date datetime NOT NULL,
    PRIMARY KEY(history_record_id)
) ENGINE=innodb;
CREATE INDEX tag_definition_history_id ON tag_definition_history(id);
CREATE INDEX tag_definition_history_record_id ON tag_definition_history(record_id);
CREATE INDEX tag_definition_history_name ON tag_definition_history(name);

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    tag_definition_name varchar(20) NOT NULL,
    object_id char(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    PRIMARY KEY(record_id)
) ENGINE = innodb;
CREATE UNIQUE INDEX tags_id ON tags(id);
CREATE INDEX tags_by_object ON tags(object_id);
CREATE UNIQUE INDEX tags_unique ON tags(tag_definition_name, object_id);

DROP TABLE IF EXISTS tag_history;
CREATE TABLE tag_history (
    history_record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    record_id int(11) unsigned NOT NULL,
    id char(36) NOT NULL,
    object_id char(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    tag_definition_name varchar(20) NOT NULL,
    updated_by varchar(50) NOT NULL,
    date datetime NOT NULL,
    change_type char(6) NOT NULL,
    PRIMARY KEY(history_record_id)
) ENGINE = innodb;
CREATE INDEX tag_history_record_id ON tag_history(record_id);
CREATE INDEX tag_history_by_object ON tags(object_id);

DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    created_date datetime NOT NULL,
	notification_key varchar(256) NOT NULL,
	creating_owner char(50) NOT NULL,
    effective_date datetime NOT NULL,
    queue_name char(64) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX notifications_id ON notifications(id);
CREATE INDEX  `idx_comp_where` ON notifications (`effective_date`, `queue_name`, `processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_update` ON notifications (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_get_ready` ON notifications (`effective_date`,`created_date`,`id`);

DROP TABLE IF EXISTS claimed_notifications;
CREATE TABLE claimed_notifications (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    owner_id varchar(64) NOT NULL,
    claimed_date datetime NOT NULL,
    notification_id char(36) NOT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;

DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    table_name varchar(50) NOT NULL,
    record_id int(11) NOT NULL,
    change_type char(6) NOT NULL,
    change_date datetime NOT NULL,
    changed_by varchar(50) NOT NULL,
    reason_code varchar(20) DEFAULT NULL,
    comments varchar(255) DEFAULT NULL,
    user_token char(36),
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX audit_log_fetch_record ON audit_log(table_name, record_id);
CREATE INDEX audit_log_user_name ON audit_log(changed_by);

DROP TABLE IF EXISTS bus_events;
CREATE TABLE bus_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    class_name varchar(128) NOT NULL, 
    event_json varchar(2048) NOT NULL,     
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE INDEX  `idx_bus_where` ON bus_events (`processing_state`,`processing_owner`,`processing_available_date`);

DROP TABLE IF EXISTS claimed_bus_events;
CREATE TABLE claimed_bus_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    owner_id varchar(64) NOT NULL,
    claimed_date datetime NOT NULL,
    bus_event_id char(36) NOT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;