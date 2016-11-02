alter table subscription_events modify plan_name varchar(255) COLLATE utf8_bin DEFAULT NULL;
alter table subscription_events modify phase_name varchar(255) COLLATE utf8_bin DEFAULT NULL;
