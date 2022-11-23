alter table subscription_events add column quantity int default 1 after billing_cycle_day_local;
alter table subscription_event_history add column quantity int default 1 after billing_cycle_day_local;
