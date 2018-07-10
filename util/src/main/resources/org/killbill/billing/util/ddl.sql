/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS custom_fields;
CREATE TABLE custom_fields (
    record_id serial unique,
    id varchar(36) NOT NULL,
    object_id varchar(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    is_active boolean default true,
    field_name varchar(64) NOT NULL,
    field_value varchar(255),
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) DEFAULT NULL,
    updated_date datetime DEFAULT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX custom_fields_id ON custom_fields(id);
CREATE INDEX custom_fields_object_id_object_type ON custom_fields(object_id, object_type);
CREATE INDEX custom_fields_tenant_account_record_id ON custom_fields(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS custom_field_history;
CREATE TABLE custom_field_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    object_id varchar(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    is_active boolean default true,
    field_name varchar(64),
    field_value varchar(255),
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX custom_field_history_target_record_id ON custom_field_history(target_record_id);
CREATE INDEX custom_field_history_object_id_object_type ON custom_fields(object_id, object_type);
CREATE INDEX custom_field_history_tenant_account_record_id ON custom_field_history(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS tag_definitions;
CREATE TABLE tag_definitions (
    record_id serial unique,
    id varchar(36) NOT NULL,
    name varchar(20) NOT NULL,
    applicable_object_types varchar(500),
    description varchar(200) NOT NULL,
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX tag_definitions_id ON tag_definitions(id);
CREATE INDEX tag_definitions_tenant_record_id ON tag_definitions(tenant_record_id);

DROP TABLE IF EXISTS tag_definition_history;
CREATE TABLE tag_definition_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    name varchar(30) NOT NULL,
    applicable_object_types varchar(500),
    description varchar(200),
    is_active boolean default true,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    /* Note: there is no account_record_id to populate */
    account_record_id bigint /*! unsigned */ default null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX tag_definition_history_id ON tag_definition_history(id);
CREATE INDEX tag_definition_history_target_record_id ON tag_definition_history(target_record_id);
CREATE INDEX tag_definition_history_name ON tag_definition_history(name);
CREATE INDEX tag_definition_history_tenant_record_id ON tag_definition_history(tenant_record_id);

DROP TABLE IF EXISTS tags;
CREATE TABLE tags (
    record_id serial unique,
    id varchar(36) NOT NULL,
    tag_definition_id varchar(36) NOT NULL,
    object_id varchar(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX tags_id ON tags(id);
CREATE INDEX tags_by_object ON tags(object_id);
CREATE INDEX tags_tenant_account_record_id ON tags(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS tag_history;
CREATE TABLE tag_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    object_id varchar(36) NOT NULL,
    object_type varchar(30) NOT NULL,
    tag_definition_id varchar(36) NOT NULL,
    is_active boolean default true,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX tag_history_target_record_id ON tag_history(target_record_id);
CREATE INDEX tag_history_by_object ON tags(object_id);
CREATE INDEX tag_history_tenant_account_record_id ON tag_history(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    table_name varchar(50) NOT NULL,
    change_type varchar(6) NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    reason_code varchar(255) DEFAULT NULL,
    comments varchar(255) DEFAULT NULL,
    user_token varchar(36),
    /* Note: can be NULL (e.g. tenant_kvs audits) */
    account_record_id bigint /*! unsigned */ default null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX audit_log_fetch_target_record_id ON audit_log(table_name, target_record_id);
CREATE INDEX audit_log_user_name ON audit_log(created_by);
CREATE INDEX audit_log_tenant_account_record_id ON audit_log(tenant_record_id, account_record_id);
CREATE INDEX audit_log_via_history ON audit_log(target_record_id, table_name, tenant_record_id);



DROP TABLE IF EXISTS notifications;
CREATE TABLE notifications (
    record_id serial unique,
    class_name varchar(256) NOT NULL,
    event_json text NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    search_key1 bigint /*! unsigned */ not null,
    search_key2 bigint /*! unsigned */ not null default 0,
    queue_name varchar(64) NOT NULL,
    effective_date datetime NOT NULL,
    future_user_token varchar(36),
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX idx_comp_where ON notifications (effective_date, processing_state, processing_owner, processing_available_date);
CREATE INDEX idx_update ON notifications (processing_state, processing_owner, processing_available_date);
CREATE INDEX idx_get_ready ON notifications (effective_date, created_date);
CREATE INDEX notifications_tenant_account_record_id ON notifications(search_key2, search_key1);

DROP TABLE IF EXISTS notifications_history;
CREATE TABLE notifications_history (
    record_id serial unique,
    class_name varchar(256) NOT NULL,
    event_json text NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    search_key1 bigint /*! unsigned */ not null,
    search_key2 bigint /*! unsigned */ not null default 0,
    queue_name varchar(64) NOT NULL,
    effective_date datetime NOT NULL,
    future_user_token varchar(36),
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX notifications_history_tenant_account_record_id ON notifications_history(search_key2, search_key1);

DROP TABLE IF EXISTS bus_events;
CREATE TABLE bus_events (
    record_id serial unique,
    class_name varchar(128) NOT NULL,
    event_json text NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    /* Note: account_record_id can be NULL (e.g. TagDefinition events) */
    search_key1 bigint /*! unsigned */ default null,
    search_key2 bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX idx_bus_where ON bus_events (processing_state, processing_owner, processing_available_date);
CREATE INDEX bus_events_tenant_account_record_id ON bus_events(search_key2, search_key1);

DROP TABLE IF EXISTS bus_events_history;
CREATE TABLE bus_events_history (
    record_id serial unique,
    class_name varchar(128) NOT NULL,
    event_json text NOT NULL,
    user_token varchar(36),
    created_date datetime NOT NULL,
    creating_owner varchar(50) NOT NULL,
    processing_owner varchar(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int /*! unsigned */ DEFAULT 0,
    /* Note: account_record_id can be NULL (e.g. TagDefinition events) */
    search_key1 bigint /*! unsigned */ default null,
    search_key2 bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX bus_events_history_tenant_account_record_id ON bus_events_history(search_key2, search_key1);

drop table if exists sessions;
create table sessions (
  record_id serial unique
, id varchar(36) NOT NULL
, start_timestamp datetime not null
, last_access_time datetime default null
, timeout int
, host varchar(100) default null
, session_data mediumblob default null
, primary key(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX sessions_id ON sessions(id);


DROP TABLE IF EXISTS users;
CREATE TABLE users (
    record_id serial unique,
    username varchar(128) NULL,
    password varchar(128) NULL,
    password_salt varchar(128) NULL,
    is_active boolean default true,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX users_username ON users(username);


DROP TABLE IF EXISTS user_roles;
CREATE TABLE user_roles (
    record_id serial unique,
    username varchar(128) NULL,
    role_name varchar(128) NULL,
    is_active boolean default true,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX user_roles_idx ON user_roles(username, role_name);


DROP TABLE IF EXISTS roles_permissions;
CREATE TABLE roles_permissions (
    record_id serial unique,
    role_name varchar(128) NULL,
    permission varchar(128) NULL,
    is_active boolean default true,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX roles_permissions_idx ON roles_permissions(role_name, permission);


DROP TABLE IF EXISTS node_infos;
CREATE TABLE node_infos (
    record_id serial unique,
    node_name varchar(50) NOT NULL,
    boot_date datetime NOT NULL,
    updated_date datetime DEFAULT NULL,
    node_info text NOT NULL,
    is_active boolean default true,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX node_name_idx ON node_infos(node_name);


DROP TABLE IF EXISTS service_broadcasts;
CREATE TABLE service_broadcasts (
    record_id serial unique,
    service_name varchar(50) NOT NULL,
    type varchar(64) NOT NULL,
    event text NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
