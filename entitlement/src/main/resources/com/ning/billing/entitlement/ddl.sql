DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS entitlement_events;
CREATE TABLE entitlement_events (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    event_type varchar(9) NOT NULL,
    user_type varchar(25) DEFAULT NULL,
    requested_date datetime NOT NULL,
    effective_date datetime NOT NULL,
    subscription_id char(36) NOT NULL,
    plan_name varchar(64) DEFAULT NULL,
    phase_name varchar(128) DEFAULT NULL,
    price_list_name varchar(64) DEFAULT NULL,
    user_token char(36),
    current_version int(11) DEFAULT 1,
    is_active bool DEFAULT 1,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX entitlement_events_id ON entitlement_events(id);
CREATE INDEX idx_ent_1 ON entitlement_events(subscription_id,is_active,effective_date);
CREATE INDEX idx_ent_2 ON entitlement_events(subscription_id,effective_date,created_date,requested_date,id);

DROP TABLE IF EXISTS subscriptions;
CREATE TABLE subscriptions (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    bundle_id char(36) NOT NULL,
    category varchar(32) NOT NULL,
    start_date datetime NOT NULL,
    bundle_start_date datetime NOT NULL,
    active_version int(11) DEFAULT 1,
    charged_through_date datetime DEFAULT NULL,
    paid_through_date datetime DEFAULT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX subscriptions_id ON subscriptions(id);

DROP TABLE IF EXISTS bundles;
CREATE TABLE bundles (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    start_date datetime, /*NOT NULL*/
    external_key varchar(64) NOT NULL,
    account_id char(36) NOT NULL,
    last_sys_update_date datetime,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
 CREATE UNIQUE INDEX bundles_id ON bundles(id);
