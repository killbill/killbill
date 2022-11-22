alter table invoice_payments add column status varchar(50) NOT NULL default "INIT" after success;
update invoice_payments set status = 'SUCCESS' where success=TRUE;
alter table invoice_payments drop column success;


alter table invoice_payment_history add column status varchar(50) NOT NULL default "INIT" after success;
update invoice_payment_history set status = 'SUCCESS' where success=TRUE;
alter table invoice_payment_history drop column success;


