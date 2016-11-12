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
    phase_number smallint /*! unsigned */ NOT NULL,
    phase_def_record_id bigint /*! unsigned */ not null,
    target_plan_def_record_id bigint /*! unsigned */ not null,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX catalog_override_plan_phase_idx ON catalog_override_plan_phase(tenant_record_id, phase_number, phase_def_record_id);
