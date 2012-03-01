package com.ning.billing.util.entity;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ErrorCode;

public class EntityPersistenceException extends BillingExceptionBase {
    private static final long serialVersionUID = 1L;

    public EntityPersistenceException(Throwable cause, int code, final String msg) {
        super(cause, code, msg);
    }

    public EntityPersistenceException(Throwable cause, ErrorCode code, final Object... args) {
        super(cause, code, args);
    }

    public EntityPersistenceException(ErrorCode code, final Object... args) {
        super(code, args);
    }
}
