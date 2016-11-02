alter table subscription_events change event_type event_type varchar(15) NOT NULL;
alter table subscription_events add column billing_cycle_day_local int DEFAULT NULL after price_list_name;