DROP TABLE IF EXISTS invoice_tracking_ids;
CREATE TABLE invoice_tracking_ids (
    record_id serial unique,
    id varchar(36) NOT NULL,
    tracking_id varchar(128) NOT NULL,
    invoice_id varchar(36) NOT NULL,
    subscription_id varchar(36),
    unit_type varchar(255) NOT NULL,
    record_date date NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_tracking_tenant_account_date_idx ON invoice_tracking_ids(tenant_record_id, account_record_id, record_date);
