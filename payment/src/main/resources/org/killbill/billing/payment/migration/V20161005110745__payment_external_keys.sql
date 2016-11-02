alter table payment_attempts modify payment_external_key varchar(255);
alter table payment_attempts modify transaction_external_key varchar(255);
alter table payment_attempt_history modify payment_external_key varchar(255);
alter table payment_attempt_history modify transaction_external_key varchar(255);