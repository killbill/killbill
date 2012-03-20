package com.ning.billing.util.entity;

import com.ning.billing.util.CallContext;
import com.ning.billing.util.CallOrigin;
import com.ning.billing.util.UserType;
import org.joda.time.DateTime;

public class MigrationCallContext extends CallContextBase {
    private final DateTime createdDate;
    private final DateTime updatedDate;

    public MigrationCallContext(String userName, CallOrigin callOrigin, UserType userType, DateTime createdDate, DateTime updatedDate) {
        super(userName, callOrigin, userType);
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public MigrationCallContext(CallContext context, DateTime createdDate, DateTime updatedDate) {
        super(context.getUserName(), context.getCallOrigin(), context.getUserType());
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }
}
