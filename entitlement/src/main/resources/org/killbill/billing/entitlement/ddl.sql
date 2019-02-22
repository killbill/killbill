/*! SET default_storage_engine=INNODB */;

DROP TABLE IF EXISTS blocking_states;
CREATE TABLE blocking_states (
    record_id serial unique,
    id varchar(36) NOT NULL,
    blockable_id varchar(36) NOT NULL,
    type varchar(20) NOT NULL,
    state varchar(50) NOT NULL,
    service varchar(20) NOT NULL,
    block_change bool NOT NULL,
    block_entitlement bool NOT NULL,
    block_billing bool NOT NULL,
    effective_date datetime NOT NULL,
    is_active boolean default true,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX blocking_states_id ON blocking_states(blockable_id);
CREATE INDEX blocking_states_id_real ON blocking_states(id);
CREATE INDEX blocking_states_tenant_account_record_id ON blocking_states(tenant_record_id, account_record_id);

DROP TABLE IF EXISTS blocking_state_history;
CREATE TABLE blocking_state_history (
    record_id serial unique,
    id varchar(36) NOT NULL,
    target_record_id bigint /*! unsigned */ not null,
    blockable_id varchar(36) NOT NULL,
    type varchar(20) NOT NULL,
    state varchar(50) NOT NULL,
    service varchar(20) NOT NULL,
    block_change bool NOT NULL,
    block_entitlement bool NOT NULL,
    block_billing bool NOT NULL,
    effective_date datetime NOT NULL,
    is_active boolean default true,
    change_type varchar(6) NOT NULL,
    created_date datetime NOT NULL,
    created_by varchar(50) NOT NULL,
    updated_date datetime DEFAULT NULL,
    updated_by varchar(50) DEFAULT NULL,
    account_record_id bigint /*! unsigned */ not null,
    tenant_record_id bigint /*! unsigned */ not null default 0,
    PRIMARY KEY(record_id)
) /*! CHARACTER SET utf8 COLLATE utf8_bin */;
CREATE INDEX blocking_state_history_target_record_id ON blocking_state_history(target_record_id);
CREATE INDEX blocking_state_history_tenant_record_id ON blocking_state_history(tenant_record_id);