alter table accounts add column reference_time datetime NOT NULL DEFAULT '1970-01-01 00:00:00' after payment_method_id;
alter table account_history add column reference_time datetime NOT NULL DEFAULT '1970-01-01 00:00:00' after payment_method_id;
update accounts set reference_time = created_date;
update account_history set reference_time = created_date;