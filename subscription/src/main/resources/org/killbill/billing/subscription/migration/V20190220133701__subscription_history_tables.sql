DROP TABLE IF EXISTS subscription_event_history;
CREATE TABLE subscription_event_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    event_type varchar(15) NOT NULL,
    user_type varchar(25) DEFAULT NULL,
    effective_date datetime NOT NULL,
    subscription_id varchar(36) NOT NULL,
    plan_name varchar(255) DEFAULT NULL,
    phase_name varchar(255) DEFAULT NULL,
    price_list_name varchar(64) DEFAULT NULL,
    billing_cycle_day_local int DEFAULT NULL,
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
CREATE INDEX subscription_event_history_target_record_id ON subscription_event_history(target_record_id);
CREATE INDEX subscription_event_history_tenant_record_id ON subscription_event_history(tenant_record_id);


DROP TABLE IF EXISTS subscription_history;
CREATE TABLE subscription_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    bundle_id varchar(36) NOT NULL,
    category varchar(32) NOT NULL,
    start_date datetime NOT NULL,
    bundle_start_date datetime NOT NULL,
    charged_through_date datetime DEFAULT NULL,
    migrated bool NOT NULL default FALSE,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX subscription_history_target_record_id ON subscription_history(target_record_id);
CREATE INDEX subscription_history_tenant_record_id ON subscription_history(tenant_record_id);

DROP TABLE IF EXISTS bundle_history;
CREATE TABLE bundle_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    external_key varchar(255) NOT NULL,
    account_id varchar(36) NOT NULL,
    last_sys_update_date datetime,
    original_created_date datetime NOT NULL,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX bundle_history_target_record_id ON bundle_history(target_record_id);
CREATE INDEX bundle_history_tenant_record_id ON bundle_history(tenant_record_id);
