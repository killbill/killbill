alter table subscriptions drop column active_version;
alter table subscriptions add column migrated bool NOT NULL default FALSE after charged_through_date;
alter table subscription_events drop column requested_date;
alter table subscription_events drop column current_version;
