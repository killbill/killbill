drop procedure if exists cleanAccount;
DELIMITER //
CREATE PROCEDURE cleanAccount(p_account_id varchar(36))
BEGIN

    DECLARE v_account_record_id bigint /*! unsigned */;
    DECLARE v_tenant_record_id bigint /*! unsigned */;

    select record_id, tenant_record_id from accounts WHERE id = p_account_id into v_account_record_id, v_tenant_record_id;

    call trimAccount(p_account_id);

    DELETE FROM account_history WHERE target_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM accounts WHERE record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM audit_log WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_method_history WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;
    DELETE FROM payment_methods WHERE account_record_id = v_account_record_id and tenant_record_id = v_tenant_record_id;

    END;
//
DELIMITER ;
