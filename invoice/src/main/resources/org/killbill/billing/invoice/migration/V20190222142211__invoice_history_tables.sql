DROP TABLE IF EXISTS invoice_tracking_id_history;
CREATE TABLE invoice_tracking_id_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    tracking_id varchar(128) NOT NULL,
    invoice_id varchar(36) NOT NULL,
    subscription_id varchar(36),
    unit_type varchar(255) NOT NULL,
    record_date date NOT NULL,
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
CREATE INDEX invoice_tracking_id_history_target_record_id ON invoice_tracking_id_history(target_record_id);
CREATE INDEX invoice_tracking_id_history_tenant_record_id ON invoice_tracking_id_history(tenant_record_id);

DROP TABLE IF EXISTS invoice_item_history;
CREATE TABLE invoice_item_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    type varchar(24) NOT NULL,
    invoice_id varchar(36) NOT NULL,
    account_id varchar(36) NOT NULL,
    child_account_id varchar(36),
    bundle_id varchar(36),
    subscription_id varchar(36),
    description varchar(255),
    product_name varchar(255),
    plan_name varchar(255),
    phase_name varchar(255),
    usage_name varchar(255),
    start_date date,
    end_date date,
    amount numeric(15,9) NOT NULL,
    rate numeric(15,9) NULL,
    currency varchar(3) NOT NULL,
    linked_item_id varchar(36),
    quantity int,
    item_details text,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_item_history_target_record_id ON invoice_item_history(target_record_id);
CREATE INDEX invoice_item_history_tenant_record_id ON invoice_item_history(tenant_record_id);


DROP TABLE IF EXISTS invoice_history;
CREATE TABLE invoice_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    account_id varchar(36) NOT NULL,
    invoice_date date NOT NULL,
    target_date date,
    currency varchar(3) NOT NULL,
    status varchar(15) NOT NULL DEFAULT 'COMMITTED',
    migrated bool NOT NULL,
    parent_invoice bool NOT NULL DEFAULT FALSE,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_history_target_record_id ON invoice_history(target_record_id);
CREATE INDEX invoice_history_tenant_record_id ON invoice_history(tenant_record_id);

DROP TABLE IF EXISTS invoice_payment_history;
CREATE TABLE invoice_payment_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    type varchar(24) NOT NULL,
    invoice_id varchar(36) NOT NULL,
    payment_id varchar(36),
    payment_date datetime NOT NULL,
    amount numeric(15,9) NOT NULL,
    currency varchar(3) NOT NULL,
    processed_currency varchar(3) NOT NULL,
    payment_cookie_id varchar(255) DEFAULT NULL,
    linked_invoice_payment_id varchar(36) DEFAULT NULL,
    success bool DEFAULT true,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_payment_history_target_record_id ON invoice_payment_history(target_record_id);
CREATE INDEX invoice_payment_history_tenant_record_id ON invoice_payment_history(tenant_record_id);
