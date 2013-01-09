/*! SET storage_engine=INNODB */;

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
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE UNIQUE INDEX custom_fields_id ON custom_fields(id);
CREATE INDEX custom_fields_object_id_object_type ON custom_fields(object_id, object_type);
CREATE UNIQUE INDEX custom_fields_unique ON custom_fields(object_id, object_type, field_name);
CREATE INDEX custom_fields_tenant_account_record_id ON custom_fields(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS custom_field_history;
CREATE TABLE custom_field_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    target_record_id int(11) unsigned NOT NULL,
    object_id char(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    field_name varchar(30),
    field_value varchar(255),
    change_type char(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX custom_field_history_target_record_id ON custom_field_history(target_record_id);
CREATE INDEX custom_field_history_object_id_object_type ON custom_fields(object_id, object_type);
CREATE INDEX custom_field_history_tenant_account_record_id ON custom_field_history(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS tag_definitions;
CREATE TABLE tag_definitions (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    name varchar(20) NOT NULL,
    description varchar(200) NOT NULL,
    is_active bool DEFAULT true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE UNIQUE INDEX tag_definitions_id ON tag_definitions(id);
CREATE INDEX tag_definitions_tenant_record_id ON tag_definitions(tenant_record_id);

DROP TABLE IF EXISTS tag_definition_history;
CREATE TABLE tag_definition_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    target_record_id int(11) unsigned NOT NULL,
    name varchar(30) NOT NULL,
    description varchar(200),
    is_active bool DEFAULT true,
    change_type char(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX tag_definition_history_id ON tag_definition_history(id);
CREATE INDEX tag_definition_history_target_record_id ON tag_definition_history(target_record_id);
CREATE INDEX tag_definition_history_name ON tag_definition_history(name);
CREATE INDEX tag_definition_history_tenant_record_id ON tag_definition_history(tenant_record_id);

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    tag_definition_id char(36) NOT NULL,
    object_id char(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    is_active bool DEFAULT true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE UNIQUE INDEX tags_id ON tags(id);
CREATE INDEX tags_by_object ON tags(object_id);
CREATE INDEX tags_tenant_account_record_id ON tags(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS tag_history;
CREATE TABLE tag_history (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    target_record_id int(11) unsigned NOT NULL,
    object_id char(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    tag_definition_id char(36) NOT NULL,
    is_active bool DEFAULT true,
    change_type char(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX tag_history_target_record_id ON tag_history(target_record_id);
CREATE INDEX tag_history_by_object ON tags(object_id);
CREATE INDEX tag_history_tenant_account_record_id ON tag_history(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    created_date datetime NOT NULL,
    class_name varchar(256) NOT NULL,
    notification_key varchar(2048) NOT NULL,
    user_token char(36),
    future_user_token char(36),
    creating_owner char(50) NOT NULL,
    effective_date datetime NOT NULL,
    queue_name char(64) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE UNIQUE INDEX notifications_id ON notifications(id);
CREATE INDEX  `idx_comp_where` ON notifications (`effective_date`, `processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_update` ON notifications (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX  `idx_get_ready` ON notifications (`effective_date`,`created_date`,`id`);
CREATE INDEX notifications_tenant_account_record_id ON notifications(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS claimed_notifications;
CREATE TABLE claimed_notifications (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    owner_id varchar(64) NOT NULL,
    claimed_date datetime NOT NULL,
    notification_id char(36) NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX claimed_notifications_tenant_account_record_id ON claimed_notifications(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS audit_log;
/*! SET storage_engine=INNODB */;

CREATE TABLE audit_log (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    target_record_id int(11) NOT NULL,
    table_name varchar(50) NOT NULL,
    change_type char(6) NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    reason_code varchar(255) DEFAULT NULL,
    comments varchar(255) DEFAULT NULL,
    user_token char(36),
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX audit_log_fetch_target_record_id ON audit_log(table_name, target_record_id);
CREATE INDEX audit_log_user_name ON audit_log(created_by);
CREATE INDEX audit_log_tenant_account_record_id ON audit_log(tenant_record_id, account_record_id);


DROP TABLE IF EXISTS bus_events;
CREATE TABLE bus_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    class_name varchar(128) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token char(36),
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX  `idx_bus_where` ON bus_events (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX bus_events_tenant_account_record_id ON bus_events(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS claimed_bus_events;
CREATE TABLE claimed_bus_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    owner_id varchar(64) NOT NULL,
    claimed_date datetime NOT NULL,
    bus_event_id char(36) NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
);
CREATE INDEX claimed_bus_events_tenant_account_record_id ON claimed_bus_events(tenant_record_id, account_record_id);
