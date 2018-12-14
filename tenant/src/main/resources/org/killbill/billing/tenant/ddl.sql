/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS tenants;
CREATE TABLE tenants (
    record_id serial unique,
    id varchar(36) NOT NULL,
    external_key varchar(255) NULL,
    api_key varchar(128) NULL,
    api_secret varchar(128) NULL,
    api_salt varchar(128) NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE UNIQUE INDEX tenants_id ON tenants(id);
CREATE UNIQUE INDEX tenants_api_key ON tenants(api_key);


DROP TABLE IF EXISTS tenant_kvs;
CREATE TABLE tenant_kvs (
   record_id serial unique,
   id varchar(36) NOT NULL,
   tenant_record_id bigint /*! unsigned */ not null default 0,
   tenant_key varchar(255) NOT NULL,
   tenant_value mediumtext NOT NULL,
   is_active boolean default true,
   created_date datetime NOT NULL,
   created_by varchar(50) NOT NULL,
   updated_date datetime DEFAULT NULL,
   updated_by varchar(50) DEFAULT NULL,
   PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX tenant_kvs_trid_key ON tenant_kvs(tenant_record_id, tenant_key);


DROP TABLE IF EXISTS tenant_broadcasts;
CREATE TABLE tenant_broadcasts (
   record_id serial unique,
   id varchar(36) NOT NULL,
   /* Note: can be NULL in case of delete */
   target_record_id bigint /*! unsigned */ default null,
   target_table_name varchar(50) NOT NULL,
   tenant_record_id bigint /*! unsigned */ not null default 0,
   type varchar(64) NOT NULL,
   user_token varchar(36),
   created_date datetime NOT NULL,
   created_by varchar(50) NOT NULL,
   updated_date datetime DEFAULT NULL,
   updated_by varchar(50) DEFAULT NULL,
   PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX tenant_broadcasts_key ON tenant_broadcasts(tenant_record_id);
