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