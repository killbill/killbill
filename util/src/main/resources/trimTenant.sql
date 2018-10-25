-- WARNING !!!
-- THIS DELETES MOST OF THE TENANT INFORMATION.
-- USE ONLY IN TESTING.
--
-- A mysql stored procedure to trim tenant information.
-- Doesn't delete the tenant and accounts.
--
-- Usage (from mysql commandline):
--   CALL trimTenant(API_KEY)
--
--  For e.g.,
--   CALL trimTenant('tenant1')

drop procedure if exists trimTenant;
DELIMITER //
CREATE PROCEDURE trimTenant(p_api_key varchar(36))
BEGIN

    DECLARE v_tenant_record_id bigint /*! unsigned */;
    DECLARE v_tenant_id varchar(36);

    select record_id from tenants WHERE api_key = p_api_key into v_tenant_record_id;
    select id from tenants WHERE api_key = p_api_key into v_tenant_id;

    DELETE FROM analytics_account_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_account_tags WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_account_transitions WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_accounts WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_bundle_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_bundle_tags WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_bundles WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_adjustments WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_credits WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_item_adjustments WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_items WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_payment_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_tags WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoices WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_notifications WHERE search_key2 = v_tenant_record_id;
    DELETE FROM analytics_notifications_history WHERE search_key2 = v_tenant_record_id;
    DELETE FROM analytics_payment_auths WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_captures WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_chargebacks WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_credits WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_method_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_purchases WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_refunds WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_tags WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_voids WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_subscription_transitions WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_transaction_fields WHERE tenant_record_id = v_tenant_record_id;

    DELETE FROM account_email_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM account_emails WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM account_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM accounts WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM audit_log WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM blocking_states WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM bundles WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM bus_events WHERE search_key2 = v_tenant_record_id;
    DELETE FROM bus_events_history WHERE search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events WHERE search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events_history WHERE search_key2 = v_tenant_record_id;
    DELETE FROM custom_field_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM custom_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_items WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_parent_children WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payments WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoices WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM notifications WHERE search_key2 = v_tenant_record_id;
    DELETE FROM notifications_history WHERE search_key2 = v_tenant_record_id;
    DELETE FROM payment_attempt_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_attempts WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_method_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_methods WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_transaction_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_transactions WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM payments WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM rolled_up_usage WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM subscription_events WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM subscriptions WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_definition_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_definitions WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tags WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tenant_broadcasts WHERE tenant_record_id = v_tenant_record_id;

    -- Uses tenant ID (instead of record id)
    DELETE FROM stripe_payment_methods WHERE kb_tenant_id = v_tenant_id;
    DELETE FROM stripe_responses WHERE kb_tenant_id = v_tenant_id;
    DELETE FROM stripe_transactions WHERE kb_tenant_id = v_tenant_id;

    END;
//
DELIMITER ;

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

    -- Trim the tenant first
    CALL trimTenant(p_api_key);

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

    DELETE FROM tenants WHERE id = v_tenant_id;

    DELETE FROM _invoice_payment_control_plugin_auto_pay_off
        WHERE account_id in (
            SELECT id from accounts where tenant_record_id = v_tenant_record_id);

    -- NOT DELETED TABLES
    -- analytics_currency_conversion
    -- analytics_reports WHERE
    -- node_infos
    -- roles_permissions
    -- service_broadcasts
    -- sessions
    -- user_roles
    -- users

    END;
//
DELIMITER ;
