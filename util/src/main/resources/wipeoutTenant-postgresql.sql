-- WARNING !!!
-- THIS DELETES ALL THE TENANT INFORMATION COMPLETELY
-- ONLY TO BE USED IN TESTING
--
-- A postgres stored procedure to wipeout the tenant completely.
--
-- Usage (from mysql commandline):
--   CALL wipeoutTenant(API_KEY)
--
--  For e.g.,
--   CALL wipeoutTenant('tenant1')

CREATE OR REPLACE PROCEDURE wipeoutTenant(p_api_key varchar(36)) LANGUAGE plpgsql
AS $$
DECLARE
    v_tenant_record_id bigint;
    v_tenant_id varchar(36);
BEGIN

    SELECT record_id FROM tenants WHERE api_key = p_api_key into v_tenant_record_id;
    SELECT id FROM tenants WHERE api_key = p_api_key into v_tenant_id;

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

END
$$;
