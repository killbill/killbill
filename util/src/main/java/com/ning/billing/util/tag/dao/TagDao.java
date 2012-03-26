package com.ning.billing.util.tag.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.List;
import java.util.UUID;

public interface TagDao {
    void saveTags(Transmogrifier dao, UUID objectId, String objectType, List<Tag> tags, CallContext context);

    void addTag(String tagName, UUID objectId, String objectType, CallContext context);

    void removeTag(String tagName, UUID objectId, String objectType, CallContext context);
}
