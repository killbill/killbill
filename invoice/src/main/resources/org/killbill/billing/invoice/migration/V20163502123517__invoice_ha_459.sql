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


alter table invoice_items add column child_account_id varchar(36) after account_id;
alter table invoice_items modify start_date date;

alter table invoices add column  parent_invoice bool NOT NULL DEFAULT FALSE after migrated;
alter table invoices modify target_date date;

drop index invoices_account_target on invoices;
create index invoices_account on invoices(account_id asc);
