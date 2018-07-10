/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS bus_ext_events;
CREATE TABLE bus_ext_events (
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
CREATE INDEX idx_bus_ext_where ON bus_ext_events (processing_state, processing_owner, processing_available_date);
CREATE INDEX bus_ext_events_tenant_account_record_id ON bus_ext_events(search_key2, search_key1);

DROP TABLE IF EXISTS bus_ext_events_history;
CREATE TABLE bus_ext_events_history (
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
CREATE INDEX bus_ext_events_history_tenant_account_record_id ON bus_ext_events_history(search_key2, search_key1);
