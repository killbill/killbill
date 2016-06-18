alter table rolled_up_usage add column tracking_id varchar(128) NOT NULL after amount;
alter table rolled_up_usage change subscription_id subscription_id varchar(36) not null;
alter table rolled_up_usage change unit_type unit_type varchar(255) not null;
