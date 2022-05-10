alter table invoices add column grp_id varchar(36) after parent_invoice;
alter table invoice_history add column grp_id varchar(36) after parent_invoice;

update invoices set grp_id=id;
update invoice_history set grp_id=id;

alter table invoices modify column grp_id varchar(36) NOT NULL;
alter table invoice_history modify column grp_id varchar(36) NOT NULL;

create index invoice_grp_id on invoices(grp_id asc);