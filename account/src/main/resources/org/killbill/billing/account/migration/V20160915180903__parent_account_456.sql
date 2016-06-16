alter table accounts add column parent_account_id varchar(36) DEFAULT NULL after billing_cycle_day_local;
alter table accounts add column is_payment_delegated_to_parent boolean DEFAULT FALSE after parent_account_id;

alter table account_history add column parent_account_id varchar(36) DEFAULT NULL after billing_cycle_day_local;
alter table account_history add column is_payment_delegated_to_parent boolean DEFAULT FALSE after parent_account_id;
