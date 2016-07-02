alter table invoices add column status varchar(15) NOT NULL after currency;
update invoices set status = 'COMMITTED';