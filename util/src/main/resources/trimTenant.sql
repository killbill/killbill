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

    DELETE FROM account_email_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM account_emails WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM account_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM accounts WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM audit_log WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM blocking_state_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM blocking_states WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM bundle_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM bundles WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM bus_events WHERE search_key2 = v_tenant_record_id;
    DELETE FROM bus_events_history WHERE search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events WHERE search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events_history WHERE search_key2 = v_tenant_record_id;
    DELETE FROM custom_field_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM custom_fields WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_item_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_items WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_parent_children WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payment_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payments WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoices WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_tracking_id_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_tracking_ids WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_billing_events WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payment_control_plugin_auto_pay_off
        WHERE account_id in (SELECT id from accounts where tenant_record_id = v_tenant_record_id);
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
    DELETE FROM subscription_event_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM subscription_events WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM subscription_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM subscriptions WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_definition_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_definitions WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_history WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tags WHERE tenant_record_id = v_tenant_record_id;
    DELETE FROM tenant_broadcasts WHERE tenant_record_id = v_tenant_record_id;


    END;
//
DELIMITER ;
