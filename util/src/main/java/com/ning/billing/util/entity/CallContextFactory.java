package com.ning.billing.util.entity;

import com.google.inject.Inject;
import com.ning.billing.util.CallContext;
import com.ning.billing.util.CallOrigin;
import com.ning.billing.util.UserType;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;

public class CallContextFactory {
    private final Clock clock;

    @Inject
    public CallContextFactory(Clock clock) {
        this.clock = clock;
    }

    public CallContext createCallContext(String userName, CallOrigin callOrigin, UserType userType) {
        return new DefaultCallContext(userName, callOrigin, userType, clock);
    }

    public CallContext createMigrationCallContext(String userName, CallOrigin callOrigin, UserType userType, DateTime createdDate, DateTime updatedDate) {
        return new MigrationCallContext(userName, callOrigin, userType, createdDate, updatedDate);
    }

    public CallContext toMigrationCallContext(CallContext callContext, DateTime createdDate, DateTime updatedDate) {
        return new MigrationCallContext(callContext, createdDate, updatedDate);
    }
}
