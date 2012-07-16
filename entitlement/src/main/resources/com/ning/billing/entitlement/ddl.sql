DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS entitlement_events;
DROP TABLE IF EXISTS subscription_events;
CREATE TABLE subscription_events (
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
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX subscription_events_id ON subscription_events(id);
CREATE INDEX idx_ent_1 ON subscription_events(subscription_id, is_active, effective_date);
CREATE INDEX idx_ent_2 ON subscription_events(subscription_id, effective_date, created_date, requested_date,id);



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
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX subscriptions_id ON subscriptions(id);
CREATE INDEX subscriptions_bundle_id ON subscriptions(bundle_id);

DROP TABLE IF EXISTS bundles;
CREATE TABLE bundles (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    id char(36) NOT NULL,
    external_key varchar(64) NOT NULL,
    account_id char(36) NOT NULL,
    last_sys_update_date datetime,
    account_record_id int(11) unsigned default null,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) ENGINE=innodb;
CREATE UNIQUE INDEX bundles_id ON bundles(id);
CREATE INDEX bundles_key ON bundles(external_key);
CREATE INDEX bundles_account ON bundles(account_id);

