/*! SET storage_engine=INNODB */;

DROP TABLE IF EXISTS sources;
CREATE TABLE sources (
  record_id int(11) unsigned not null auto_increment
, source char(36) not null
, created_date datetime default null
, created_by varchar(50) default null
, updated_date datetime default null
, updated_by varchar(50) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
CREATE UNIQUE INDEX source_unq on sources(source);
CREATE INDEX created_date_record_id_dx on sources(created_date, record_id);
CREATE INDEX sources_tenant_account_record_id on sources(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS categories;
CREATE TABLE categories (
  record_id int(11) unsigned not null auto_increment
, category varchar(255) not null
, created_date datetime default null
, created_by varchar(50) default null
, updated_date datetime default null
, updated_by varchar(50) default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
CREATE UNIQUE INDEX event_category_unq on categories(category);
CREATE INDEX categories_tenant_record_id on categories(tenant_record_id);

DROP TABLE IF EXISTS metrics;
CREATE TABLE metrics (
  record_id int(11) unsigned not null auto_increment
, category_record_id integer not null
, metric varchar(255) not null
, created_date datetime default null
, created_by varchar(50) default null
, updated_date datetime default null
, updated_by varchar(50) default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
CREATE UNIQUE INDEX metric_unq on metrics(category_record_id, metric);
CREATE INDEX metrics_tenant_record_id on metrics(tenant_record_id);

DROP TABLE IF EXISTS timeline_chunks;
CREATE TABLE timeline_chunks (
  record_id bigint not null auto_increment
, source_record_id integer not null
, metric_record_id integer not null
, sample_count integer not null
, start_time integer not null
, end_time integer not null
, not_valid tinyint default 0
, aggregation_level tinyint default 0
, dont_aggregate tinyint default 0
, in_row_samples varbinary(400) default null
, blob_samples mediumblob default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
);
CREATE UNIQUE INDEX source_record_id_timeline_chunk_metric_record_idx on timeline_chunks(source_record_id, metric_record_id, start_time, aggregation_level);
CREATE INDEX valid_agg_host_start_time on timeline_chunks(not_valid, aggregation_level, source_record_id, metric_record_id, start_time);

DROP TABLE IF EXISTS last_start_times;
CREATE TABLE last_start_times (
  time_inserted int not null primary key
, start_times mediumtext not null
);

INSERT INTO timeline_chunks(record_id, source_record_id, metric_record_id, sample_count, start_time, end_time, in_row_samples, blob_samples)
VALUES (0, 0, 0, 0, 0, 0, null, null);
