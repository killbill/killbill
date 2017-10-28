/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS subscription_events;
CREATE TABLE subscription_events (
    record_id serial unique,
    id varchar(36) NOT NULL,
    event_type varchar(15) NOT NULL,
    user_type varchar(25) DEFAULT NULL,
    effective_date datetime NOT NULL,
    subscription_id varchar(36) NOT NULL,
    plan_name varchar(255) DEFAULT NULL,
    phase_name varchar(255) DEFAULT NULL,
    price_list_name varchar(64) DEFAULT NULL,
    billing_cycle_day_local int DEFAULT NULL,
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX subscription_events_id ON subscription_events(id);
CREATE INDEX idx_ent_1 ON subscription_events(subscription_id, is_active, effective_date);
CREATE INDEX idx_ent_2 ON subscription_events(subscription_id, effective_date, created_date, id);
CREATE INDEX subscription_events_tenant_account_record_id ON subscription_events(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS subscriptions;
CREATE TABLE subscriptions (
    record_id serial unique,
    id varchar(36) NOT NULL,
    bundle_id varchar(36) NOT NULL,
    category varchar(32) NOT NULL,
    start_date datetime NOT NULL,
    bundle_start_date datetime NOT NULL,
    charged_through_date datetime DEFAULT NULL,
    migrated bool NOT NULL default FALSE,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX subscriptions_id ON subscriptions(id);
CREATE INDEX subscriptions_bundle_id ON subscriptions(bundle_id);
CREATE INDEX subscriptions_tenant_account_record_id ON subscriptions(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS bundles;
CREATE TABLE bundles (
    record_id serial unique,
    id varchar(36) NOT NULL,
    external_key varchar(255) NOT NULL,
    account_id varchar(36) NOT NULL,
    last_sys_update_date datetime,
    original_created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX bundles_id ON bundles(id);
CREATE UNIQUE INDEX bundles_external_key ON bundles(external_key, tenant_record_id);
CREATE INDEX bundles_account ON bundles(account_id);
CREATE INDEX bundles_tenant_account_record_id ON bundles(tenant_record_id, account_record_id);

