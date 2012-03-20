package com.ning.billing.util.entity;

import com.ning.billing.util.CallContext;
import com.ning.billing.util.CallOrigin;
import com.ning.billing.util.UserType;

public abstract class CallContextBase implements CallContext {
    private final String userName;
    private final CallOrigin callOrigin;
    private final UserType userType;

    public CallContextBase(String userName, CallOrigin callOrigin, UserType userType) {
        this.userName = userName;
        this.callOrigin = callOrigin;
        this.userType = userType;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public CallOrigin getCallOrigin() {
        return callOrigin;
    }

    @Override
    public UserType getUserType() {
        return userType;
    }
}
