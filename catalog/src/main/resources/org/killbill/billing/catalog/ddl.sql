/*! SET storage_engine=INNODB */;

DROP TABLE IF EXISTS catalog_override_plan_definition;
CREATE TABLE catalog_override_plan_definition (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    parent_plan_name varchar(255) NOT NULL,
    effective_date datetime NOT NULL,
    is_active bool DEFAULT 1,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX catalog_override_plan_definition_tenant_record_id ON catalog_override_plan_definition(tenant_record_id);


DROP TABLE IF EXISTS catalog_override_phase_definition;
CREATE TABLE catalog_override_phase_definition (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    parent_phase_name varchar(255) NOT NULL,
    currency char(3) NOT NULL,
    fixed_price numeric(15,9) NULL,
    recurring_price numeric(15,9) NULL,
    effective_date datetime NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX catalog_override_phase_definition_idx ON catalog_override_phase_definition(tenant_record_id, parent_phase_name, currency);

DROP TABLE IF EXISTS catalog_override_plan_phase;
CREATE TABLE catalog_override_plan_phase (
    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,
    phase_number tinyint(3) unsigned NOT NULL,
    phase_def_record_id int(11) unsigned NOT NULL,
    target_plan_def_record_id int(11) unsigned NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    tenant_record_id int(11) unsigned default null,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX catalog_override_plan_phase_idx ON catalog_override_plan_phase(tenant_record_id, phase_number, phase_def_record_id);
