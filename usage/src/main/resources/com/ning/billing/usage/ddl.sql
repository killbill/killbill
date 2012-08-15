create table sources (
  record_id int(11) unsigned not null auto_increment
, bundle_id char(36) default null
, subscription_id char(36) default null
, created_date datetime default null
, created_by varchar(50) default null
, updated_date datetime default null
, updated_by varchar(50) default null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
, index created_date_record_id_dx (created_date, record_id)
) engine = innodb default charset = latin1;

create table event_categories (
  record_id integer not null auto_increment
, event_category varchar(256) not null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
, unique index event_category_unq (event_category)
) engine = innodb default charset = latin1;

create table metrics (
  record_id int(11) unsigned not null auto_increment
, event_category_id integer not null
, metric varchar(256) not null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
, unique index metric_unq (event_category_id, metric)
) engine = innodb default charset = latin1;

create table timeline_chunks (
  record_id bigint not null auto_increment
, source_id integer not null
, metric_id integer not null
, sample_count integer not null
, start_time integer not null
, end_time integer not null
, not_valid tinyint default 0
, aggregation_level tinyint default 0
, dont_aggregate tinyint default 0
, in_row_samples varbinary(400) default null
, blob_samples mediumblob default null
, primary key(record_id)
, unique index source_id_timeline_chunk_metric_idx (source_id, metric_id, start_time, aggregation_level)
, index valid_agg_host_start_time (not_valid, aggregation_level, source_id, metric_id, start_time)
) engine = innodb default charset = latin1;

create table last_start_times (
  time_inserted int not null primary key
, start_times mediumtext not null
) engine = innodb default charset = latin1;

insert ignore into timeline_chunks(record_id, source_id, metric_id, sample_count, start_time, end_time, in_row_samples, blob_samples)
                           values (0, 0, 0, 0, 0, 0, null, null);

create table timeline_rolled_up_chunk (
  record_id bigint not null auto_increment
, source_id integer not null
, metric_id integer not null
, start_time date not null
, end_time date not null
, value bigint not null
, account_record_id int(11) unsigned default null
, tenant_record_id int(11) unsigned default null
, primary key(record_id)
) engine = innodb default charset = latin1;
