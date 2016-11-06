alter table sessions add column id varchar(36) not null after record_id;
update sessions set id = record_id;
create unique index sessions_id on sessions(id);