CREATE OR REPLACE PROCEDURE trimAccount(p_account_id varchar(36)) LANGUAGE plpgsql
AS $$
DECLARE
    v_account_record_id bigint;
    v_tenant_record_id bigint;

BEGIN
    select record_id, tenant_record_id from accounts WHERE id = p_account_id into v_account_record_id, v_tenant_record_id;

    DELETE FROM account_email_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM account_emails WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM audit_log WHERE table_name not in ('ACCOUNT_HISTORY', 'PAYMENT_METHOD_HISTORY') and account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM blocking_state_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM blocking_states WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM bundle_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM bundles WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM bus_events WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM bus_events_history WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events_history WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM custom_field_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM custom_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_billing_events WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_item_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_items WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_parent_children WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payment_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payments WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoices WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_tracking_id_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_tracking_ids WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payment_control_plugin_auto_pay_off WHERE account_id = p_account_id;
    DELETE FROM notifications WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM notifications_history WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM payment_attempt_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_attempts WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_transaction_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_transactions WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payments WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM rolled_up_usage WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM subscription_event_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM subscription_events WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM subscription_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM subscriptions WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM tags WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;

END
$$;
