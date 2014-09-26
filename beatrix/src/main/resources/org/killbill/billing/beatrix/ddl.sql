/*! SET storage_engine=INNODB */;

DROP TABLE IF EXISTS bus_ext_events;
CREATE TABLE bus_ext_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    class_name varchar(128) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token char(36),
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int(11) unsigned DEFAULT 0,
    search_key1 int(11) unsigned default null,
    search_key2 int(11) unsigned default null,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX  `idx_bus_ext_where` ON bus_ext_events (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX bus_ext_events_tenant_account_record_id ON bus_ext_events(search_key2, search_key1);

DROP TABLE IF EXISTS bus_ext_events_history;
CREATE TABLE bus_ext_events_history (
    record_id int(11) unsigned NOT NULL,
    class_name varchar(128) NOT NULL,
    event_json varchar(2048) NOT NULL,
    user_token char(36),
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    error_count int(11) unsigned DEFAULT 0,
    search_key1 int(11) unsigned default null,
    search_key2 int(11) unsigned default null,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
