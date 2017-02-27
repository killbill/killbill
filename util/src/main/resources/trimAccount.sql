drop procedure if exists trimAccount;
DELIMITER //
CREATE PROCEDURE trimAccount(p_account_key varchar(36))
BEGIN

    DECLARE v_account_record_id bigint /*! unsigned */;
    DECLARE v_tenant_record_id bigint /*! unsigned */;

    select record_id, tenant_record_id from accounts WHERE external_key = p_account_key into v_account_record_id, v_tenant_record_id;

    DELETE FROM analytics_account_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_account_tags WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_account_transitions WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_accounts WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_bundle_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_bundle_tags WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_bundles WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_adjustments WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_credits WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_item_adjustments WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_items WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_payment_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoice_tags WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_invoices WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_notifications WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM analytics_notifications_history WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM analytics_payment_auths WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_captures WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_chargebacks WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_credits WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_method_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_purchases WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_refunds WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_tags WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_payment_voids WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_subscription_transitions WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM analytics_transaction_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;

    DELETE FROM account_email_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM account_emails WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM audit_log WHERE table_name not in ('ACCOUNT_HISTORY', 'PAYMENT_METHOD_HISTORY') and account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM blocking_states WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM bundles WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM bus_events WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM bus_events_history WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM bus_ext_events_history WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM custom_field_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM custom_fields WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_items WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_parent_children WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoice_payments WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM invoices WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM notifications WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM notifications_history WHERE search_key1 = v_account_record_id and search_key2 = v_tenant_record_id;
    DELETE FROM payment_attempt_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_attempts WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_transaction_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_transactions WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payments WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM rolled_up_usage WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM subscription_events WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM subscriptions WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM tag_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM tags WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;

    END;
//
DELIMITER ;
