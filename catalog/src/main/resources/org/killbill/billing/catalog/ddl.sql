/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS catalog_override_plan_definition;
CREATE TABLE catalog_override_plan_definition (
    record_id serial unique,
    parent_plan_name varchar(255) NOT NULL,
    effective_date datetime NOT NULL,
    is_active boolean default true,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX catalog_override_plan_definition_tenant_record_id ON catalog_override_plan_definition(tenant_record_id);


DROP TABLE IF EXISTS catalog_override_phase_definition;
CREATE TABLE catalog_override_phase_definition (
    record_id serial unique,
    parent_phase_name varchar(255) NOT NULL,
    currency varchar(3) NOT NULL,
    fixed_price numeric(15,9) NULL,
    recurring_price numeric(15,9) NULL,
    effective_date datetime NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX catalog_override_phase_definition_idx ON catalog_override_phase_definition(tenant_record_id, parent_phase_name, currency);

DROP TABLE IF EXISTS catalog_override_plan_phase;
CREATE TABLE catalog_override_plan_phase (
    record_id serial unique,
    phase_number int /*! unsigned */ NOT NULL,
    phase_def_record_id bigint /*! unsigned */ not null,
    target_plan_def_record_id bigint /*! unsigned */ not null,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX catalog_override_plan_phase_idx ON catalog_override_plan_phase(tenant_record_id, phase_number, phase_def_record_id);

DROP TABLE IF EXISTS catalog_override_usage_definition;
create table catalog_override_usage_definition
(
record_id serial unique,
parent_usage_name varchar(255) NOT NULL,
type varchar(255) NOT NULL,
fixed_price decimal(15,9) NULL,
recurring_price decimal(15,9) NULL,
currency varchar(3) NOT NULL,
effective_date datetime NOT NULL,
created_date datetime NOT NULL,
created_by varchar(50) NOT NULL,
tenant_record_id bigint /*! unsigned */ not null default 0,
PRIMARY KEY(record_id)
);
CREATE INDEX catalog_override_usage_definition_idx ON catalog_override_usage_definition(tenant_record_id, parent_usage_name, currency);


DROP TABLE IF EXISTS catalog_override_tier_definition;
create table catalog_override_tier_definition
(
record_id serial unique,
fixed_price decimal(15,9) NULL,
recurring_price decimal(15,9) NULL,
currency varchar(3) NOT NULL,
effective_date datetime NOT NULL,
created_date datetime NOT NULL,
created_by varchar(50) NOT NULL,
tenant_record_id bigint /*! unsigned */ not null default 0,
PRIMARY KEY(record_id)
);
CREATE INDEX catalog_override_tier_definition_idx ON catalog_override_usage_definition(tenant_record_id, currency);

DROP TABLE IF EXISTS catalog_override_block_definition;
create table catalog_override_block_definition
(
record_id serial unique,
parent_unit_name varchar(255) NOT NULL,
size decimal(15,9) NOT NULL,
max decimal(15,9) NULL,
currency varchar(3) NOT NULL,
price decimal(15,9) NOT NULL,
effective_date datetime NOT NULL,
created_date datetime NOT NULL,
created_by varchar(50) NOT NULL,
tenant_record_id bigint /*! unsigned */ not null default 0,
PRIMARY KEY(record_id)
);
CREATE INDEX catalog_override_block_definition_idx ON catalog_override_block_definition(tenant_record_id, parent_unit_name, currency);


DROP TABLE IF EXISTS catalog_override_phase_usage;
create table catalog_override_phase_usage
(
record_id serial unique,
usage_number int /*! unsigned */,
usage_def_record_id  bigint /*! unsigned */ not null,
target_phase_def_record_id bigint /*! unsigned */ not null,
created_date datetime NOT NULL,
created_by varchar(50) NOT NULL,
tenant_record_id bigint /*! unsigned */ not null default 0,
PRIMARY KEY(record_id)
);
CREATE INDEX catalog_override_phase_usage_idx ON catalog_override_phase_usage(tenant_record_id, usage_number, usage_def_record_id);

DROP TABLE IF EXISTS catalog_override_usage_tier;
create table catalog_override_usage_tier
(
record_id serial unique,
tier_number int /*! unsigned */,
tier_def_record_id bigint /*! unsigned */ not null,
target_usage_def_record_id bigint /*! unsigned */ not null,
created_date datetime NOT NULL,
created_by varchar(50) NOT NULL,
tenant_record_id bigint /*! unsigned */ not null default 0,
PRIMARY KEY(record_id)
);
CREATE INDEX catalog_override_usage_tier_idx ON catalog_override_usage_tier(tenant_record_id, tier_number, tier_def_record_id);


DROP TABLE IF EXISTS catalog_override_tier_block;
create table catalog_override_tier_block
(
record_id serial unique,
block_number int /*! unsigned */,
block_def_record_id bigint /*! unsigned */ not null,
target_tier_def_record_id bigint /*! unsigned */ not null,
created_date datetime NOT NULL,
created_by varchar(50) NOT NULL,
tenant_record_id bigint /*! unsigned */ NOT NULL default 0,
PRIMARY KEY(record_id)
);
CREATE INDEX catalog_override_tier_block_idx ON catalog_override_tier_block(tenant_record_id, block_number, block_def_record_id);


