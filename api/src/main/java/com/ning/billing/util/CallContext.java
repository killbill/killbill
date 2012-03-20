package com.ning.billing.util;

import org.joda.time.DateTime;

public interface CallContext {
    public String getUserName();
    public CallOrigin getCallOrigin();
    public UserType getUserType();
    public DateTime getCreatedDate();
    public DateTime getUpdatedDate();
}
