alter table invoice_items modify plan_name varchar(255) COLLATE utf8_bin DEFAULT NULL;
alter table invoice_items modify phase_name varchar(255) COLLATE utf8_bin DEFAULT NULL;
alter table invoice_items modify usage_name varchar(255) COLLATE utf8_bin DEFAULT NULL;