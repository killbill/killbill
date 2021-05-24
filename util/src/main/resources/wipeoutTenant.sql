-- WARNING !!!
-- THIS DELETES ALL THE TENANT INFORMATION COMPLETELY
-- ONLY TO BE USED IN TESTING
--
-- A mysql stored procedure to wipeout the tenant completely.
--
-- Usage (from mysql commandline):
--   CALL wipeoutTenant(API_KEY)
--
--  For e.g.,
--   CALL wipeoutTenant('tenant1')

drop procedure if exists wipeoutTenant;
DELIMITER //
CREATE PROCEDURE wipeoutTenant(p_api_key varchar(36))
BEGIN

    DECLARE v_tenant_record_id bigint /*! unsigned */;
    DECLARE v_tenant_id varchar(36);

    select record_id from tenants WHERE api_key = p_api_key into v_tenant_record_id;
    select id from tenants WHERE api_key = p_api_key into v_tenant_id;

    DELETE FROM catalog_override_block_definition WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_phase_definition WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_phase_usage WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_plan_definition WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_plan_phase WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_tier_block WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_tier_definition WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_usage_definition WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM catalog_override_usage_tier WHERE tenant_record_id = v_tenant_record_id;

    DELETE FROM tenant_kvs WHERE tenant_record_id = v_tenant_record_id;

    -- Trim the tenant
    CALL trimTenant(p_api_key);

    DELETE FROM tenants WHERE id = v_tenant_id;

    -- NOT DELETED TABLES
    -- node_infos
    -- roles_permissions
    -- service_broadcasts
    -- sessions
    -- user_roles
    -- users

    END;
//
DELIMITER ;
