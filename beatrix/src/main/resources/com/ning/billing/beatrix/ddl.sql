

DROP TABLE IF EXISTS bus_ext_events;
CREATE TABLE bus_ext_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    event_type varchar(32) NOT NULL,
    object_id varchar(64) NOT NULL,
    object_type varchar(32) NOT NULL,
    created_date datetime NOT NULL,
    creating_owner char(50) NOT NULL,
    processing_owner char(50) DEFAULT NULL,
    processing_available_date datetime DEFAULT NULL,
    processing_state varchar(14) DEFAULT 'AVAILABLE',
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE INDEX  `idx_bus_ext_where` ON bus_ext_events (`processing_state`,`processing_owner`,`processing_available_date`);
CREATE INDEX bus_ext_events_tenant_account_record_id ON bus_ext_events(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS claimed_bus_ext_events;
CREATE TABLE claimed_bus_ext_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    owner_id varchar(64) NOT NULL,
    claimed_date datetime NOT NULL,
    bus_event_id char(36) NOT NULL,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE INDEX claimed_bus_ext_events_tenant_account_record_id ON claimed_bus_ext_events(tenant_record_id, account_record_id);
