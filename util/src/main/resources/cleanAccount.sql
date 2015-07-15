drop procedure if exists cleanAccount;
DELIMITER //
CREATE PROCEDURE cleanAccount(p_account_key varchar(36))
BEGIN

    DECLARE v_account_record_id bigint /*! unsigned */;

    select record_id from accounts WHERE external_key = p_account_key into v_account_record_id;

    DELETE FROM analytics_account_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_account_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_account_transitions WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_accounts WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_bundle_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_bundle_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_bundles WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_chargebacks WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_invoice_adjustments WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_invoice_credits WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_invoice_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_invoice_item_adjustments WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_invoice_items WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_invoice_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_invoices WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_payment_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_payment_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_payments WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_refunds WHERE account_record_id = v_account_record_id;
    DELETE FROM analytics_subscription_transitions WHERE account_record_id = v_account_record_id;

    DELETE FROM accounts WHERE record_id = v_account_record_id;
    DELETE FROM account_emails WHERE account_record_id = v_account_record_id;
    DELETE FROM account_email_history WHERE account_record_id = v_account_record_id;
    DELETE FROM account_history WHERE target_record_id = v_account_record_id;
    DELETE FROM audit_log WHERE account_record_id = v_account_record_id;
    DELETE FROM bac WHERE account_record_id = v_account_record_id;
    DELETE FROM bac_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM bac_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM bii WHERE account_record_id = v_account_record_id;
    DELETE FROM bin WHERE account_record_id = v_account_record_id;
    DELETE FROM bin_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM bin_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM bip WHERE account_record_id = v_account_record_id;
    DELETE FROM bip_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM bip_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM blocking_states WHERE account_record_id = v_account_record_id;
    DELETE FROM bos WHERE account_record_id = v_account_record_id;
    DELETE FROM bst WHERE account_record_id = v_account_record_id;
    DELETE FROM bst_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM bst_tags WHERE account_record_id = v_account_record_id;
    DELETE FROM bundles WHERE account_record_id = v_account_record_id;
    DELETE FROM custom_field_history WHERE account_record_id = v_account_record_id;
    DELETE FROM custom_fields WHERE account_record_id = v_account_record_id;
    DELETE FROM invoice_payments WHERE account_record_id = v_account_record_id;
    DELETE FROM invoices WHERE account_record_id = v_account_record_id;
    DELETE FROM invoice_items WHERE account_record_id = v_account_record_id;
    DELETE FROM payment_attempt_history WHERE account_record_id = v_account_record_id;
    DELETE FROM payment_attempts WHERE account_record_id = v_account_record_id;
    DELETE FROM payment_methods WHERE account_record_id = v_account_record_id;
    DELETE FROM payment_method_history WHERE account_record_id = v_account_record_id;
    DELETE FROM payment_history WHERE account_record_id = v_account_record_id;
    DELETE FROM payments WHERE account_record_id = v_account_record_id;
    DELETE FROM refunds WHERE account_record_id = v_account_record_id;
    DELETE FROM refund_history WHERE account_record_id = v_account_record_id;
    DELETE FROM subscriptions WHERE account_record_id = v_account_record_id;
    DELETE FROM subscription_events WHERE account_record_id = v_account_record_id;
    DELETE FROM tags WHERE account_record_id = v_account_record_id;
    DELETE FROM tag_history WHERE account_record_id = v_account_record_id;

    END;
//
DELIMITER ;
