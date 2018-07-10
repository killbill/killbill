alter table accounts add column reference_time datetime NOT NULL after payment_method_id;
alter table account_history add column reference_time datetime NOT NULL after payment_method_id;
update accounts set reference_time = created_date;
update account_history set reference_time = created_date;