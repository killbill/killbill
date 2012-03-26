package com.ning.billing.util.tag.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.tag.ControlTagType;

import java.util.UUID;

public interface TaggableDao {
    public void addControlTag(ControlTagType controlTagType, UUID objectId, CallContext context);

    public void removeControlTag(ControlTagType controlTagType, UUID objectId, CallContext context);
}
