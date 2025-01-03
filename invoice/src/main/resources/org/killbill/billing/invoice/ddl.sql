/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS invoice_tracking_ids;
CREATE TABLE invoice_tracking_ids (
    record_id serial unique,
    id varchar(36) NOT NULL,
    tracking_id varchar(128) NOT NULL,
    invoice_id varchar(36) NOT NULL,
    subscription_id varchar(36),
    unit_type varchar(255) NOT NULL,
    record_date date NOT NULL,
    is_active boolean default true,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    updated_by varchar(50) NOT NULL,
    updated_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_tracking_tenant_account_date_idx ON invoice_tracking_ids(tenant_record_id, account_record_id, record_date);
CREATE INDEX invoice_tracking_invoice_id_idx ON invoice_tracking_ids(invoice_id);
CREATE INDEX invoice_tracking_id_idx ON invoice_tracking_ids(id);


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

DROP TABLE IF EXISTS invoice_items;
CREATE TABLE invoice_items (
    record_id serial unique,
    id varchar(36) NOT NULL,
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
    catalog_effective_date datetime,
    start_date date,
    end_date date,
    amount numeric(15,9) NOT NULL,
    rate numeric(15,9) NULL,
    currency varchar(3) NOT NULL,
    linked_item_id varchar(36),
    quantity decimal(18, 9),
    item_details text,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX invoice_items_id ON invoice_items(id);
CREATE INDEX invoice_items_subscription_id ON invoice_items(subscription_id ASC);
CREATE INDEX invoice_items_invoice_id ON invoice_items(invoice_id ASC);
CREATE INDEX invoice_items_account_id ON invoice_items(account_id ASC);
CREATE INDEX invoice_items_linked_item_id ON invoice_items(linked_item_id ASC);
CREATE INDEX invoice_items_tenant_account_record_id ON invoice_items(tenant_record_id, account_record_id);

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
    catalog_effective_date datetime,
    start_date date,
    end_date date,
    amount numeric(15,9) NOT NULL,
    rate numeric(15,9) NULL,
    currency varchar(3) NOT NULL,
    linked_item_id varchar(36),
    quantity decimal(18, 9),
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



DROP TABLE IF EXISTS invoices;
CREATE TABLE invoices (
    record_id serial unique,
    id varchar(36) NOT NULL,
    account_id varchar(36) NOT NULL,
    invoice_date date NOT NULL,
    target_date date,
    currency varchar(3) NOT NULL,
    status varchar(15) NOT NULL DEFAULT 'COMMITTED',
    migrated bool NOT NULL,
    parent_invoice bool NOT NULL DEFAULT FALSE,
    grp_id varchar(36) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX invoices_id ON invoices(id);
CREATE INDEX invoices_account ON invoices(account_id ASC);
CREATE INDEX invoices_tenant_account_record_id ON invoices(tenant_record_id, account_record_id);
CREATE INDEX invoice_grp_id ON invoices(grp_id ASC);
CREATE INDEX invoice_currency ON invoices(currency ASC);
CREATE INDEX invoice_account_record_id_record_id ON invoices(account_record_id, record_id);

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
    grp_id varchar(36) NOT NULL,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_history_target_record_id ON invoice_history(target_record_id);
CREATE INDEX invoice_history_tenant_record_id ON invoice_history(tenant_record_id);
CREATE INDEX invoice_history_currency ON invoice_history(currency ASC);
CREATE INDEX invoice_history_account_record_id_record_id ON invoice_history(account_record_id, record_id);

DROP TABLE IF EXISTS invoice_payments;
CREATE TABLE invoice_payments (
    record_id serial unique,
    id varchar(36) NOT NULL,
    type varchar(24) NOT NULL,
    invoice_id varchar(36) NOT NULL,
    payment_id varchar(36),
    payment_date datetime NOT NULL,
    amount numeric(15,9) NOT NULL,
    currency varchar(3) NOT NULL,
    processed_currency varchar(3) NOT NULL,
    payment_cookie_id varchar(255) DEFAULT NULL,
    linked_invoice_payment_id varchar(36) DEFAULT NULL,
    status varchar(50) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX invoice_payments_id ON invoice_payments(id);
CREATE INDEX invoice_payments_invoice_id ON invoice_payments(invoice_id);
CREATE INDEX invoice_payments_reversals ON invoice_payments(linked_invoice_payment_id);
CREATE INDEX invoice_payments_payment_id ON invoice_payments(payment_id);
CREATE INDEX invoice_payments_payment_cookie_id ON invoice_payments(payment_cookie_id);
CREATE INDEX invoice_payments_tenant_account_record_id ON invoice_payments(tenant_record_id, account_record_id);

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
    status varchar(50) NOT NULL,
    change_type varchar(6) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX invoice_payment_history_target_record_id ON invoice_payment_history(target_record_id);
CREATE INDEX invoice_payment_history_tenant_record_id ON invoice_payment_history(tenant_record_id);


DROP TABLE IF EXISTS invoice_parent_children;
CREATE TABLE invoice_parent_children (
    record_id serial unique,
    id varchar(36) NOT NULL,
    parent_invoice_id varchar(36) NOT NULL,
    child_invoice_id varchar(36) NOT NULL,
    child_account_id varchar(36) NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX invoice_parent_children_id ON invoice_parent_children(id);
CREATE INDEX invoice_parent_children_invoice_id ON invoice_parent_children(parent_invoice_id);
CREATE INDEX invoice_parent_children_tenant_account_record_id ON invoice_parent_children(tenant_record_id, account_record_id);
CREATE INDEX invoice_parent_children_child_invoice_id ON invoice_parent_children(child_invoice_id);

DROP TABLE IF EXISTS invoice_billing_events;
CREATE TABLE invoice_billing_events (
    record_id serial unique,
    id varchar(36) NOT NULL,
    invoice_id varchar(36) NOT NULL,
	billing_events blob NOT NULL,
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX invoice_billing_events_invoice_id ON invoice_billing_events(invoice_id);
CREATE INDEX invoice_billing_events_tenant_account_record_id ON invoice_billing_events(tenant_record_id, account_record_id);