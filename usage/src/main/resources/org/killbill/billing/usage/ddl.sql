/*! SET storage_engine=INNODB */;

DROP TABLE IF EXISTS rolled_up_usage;
CREATE TABLE rolled_up_usage (
    record_id serial unique,
    id char(36) NOT NULL,
    subscription_id char(36),
    unit_type varchar(50),
    record_date date NOT NULL,
    amount bigint NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint unsigned not null,
    tenant_record_id bigint unsigned not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX rolled_up_usage_id ON rolled_up_usage(id);
CREATE INDEX rolled_up_usage_subscription_id ON rolled_up_usage(subscription_id ASC);
CREATE INDEX rolled_up_usage_tenant_account_record_id ON rolled_up_usage(tenant_record_id, account_record_id);
CREATE INDEX rolled_up_usage_account_record_id ON rolled_up_usage(account_record_id);
