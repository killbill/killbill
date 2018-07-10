/*! SET default_storage_engine=INNODB */;

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
    start_date date,
    end_date date,
    amount numeric(15,9) NOT NULL,
    rate numeric(15,9) NULL,
    currency varchar(3) NOT NULL,
    linked_item_id varchar(36),
    quantity int,
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
    created_by varchar(50) NOT NULL,
    created_date datetime NOT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX invoices_id ON invoices(id);
CREATE INDEX invoices_account ON invoices(account_id ASC);
CREATE INDEX invoices_tenant_account_record_id ON invoices(tenant_record_id, account_record_id);

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
    success bool DEFAULT true,
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
