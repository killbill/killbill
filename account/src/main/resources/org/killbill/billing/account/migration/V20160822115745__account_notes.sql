alter table accounts add column notes varchar(4096) DEFAULT NULL after phone;
alter table account_history add column notes varchar(4096) DEFAULT NULL after phone;