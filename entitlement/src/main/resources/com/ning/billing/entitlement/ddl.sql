DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS entitlement_events;
CREATE TABLE entitlement_events (
    id int(11) unsigned NOT NULL AUTO_INCREMENT,
    event_id char(36) NOT NULL,
    event_type varchar(9) NOT NULL,
    user_type varchar(25) DEFAULT NULL,
    created_dt datetime NOT NULL,
    updated_dt datetime NOT NULL,
    requested_dt datetime NOT NULL,
    effective_dt datetime NOT NULL,
    subscription_id char(36) NOT NULL,
    plan_name varchar(64) DEFAULT NULL,
    phase_name varchar(128) DEFAULT NULL,
    plist_name varchar(64) DEFAULT NULL,
    current_version int(11) DEFAULT 1,
    is_active bool DEFAULT 1,
    PRIMARY KEY(id)
) ENGINE=innodb;
CREATE INDEX idx_ent_1 ON entitlement_events(subscription_id,is_active,effective_dt);
CREATE INDEX idx_ent_2 ON entitlement_events(subscription_id,effective_dt,created_dt,requested_dt,id);


DROP TABLE IF EXISTS subscriptions;
CREATE TABLE subscriptions (
    id char(36) NOT NULL,
    bundle_id char(36) NOT NULL,
    category varchar(32) NOT NULL,
    start_dt datetime NOT NULL,
    bundle_start_dt datetime NOT NULL,
    active_version int(11) DEFAULT 1,
    ctd_dt datetime DEFAULT NULL,
    ptd_dt datetime DEFAULT NULL,
    PRIMARY KEY(id)
) ENGINE=innodb;

DROP TABLE IF EXISTS bundles;
CREATE TABLE bundles (
    id char(36) NOT NULL,
    start_dt datetime, /*NOT NULL*/
    name varchar(64) NOT NULL,
    account_id char(36) NOT NULL,
    PRIMARY KEY(id)
) ENGINE=innodb;
