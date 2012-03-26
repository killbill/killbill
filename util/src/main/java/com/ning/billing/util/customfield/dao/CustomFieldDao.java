package com.ning.billing.util.customfield.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.List;
import java.util.UUID;

public interface CustomFieldDao {
    void saveFields(Transmogrifier dao, UUID objectId, String objectType, List<CustomField> fields, CallContext context);
}
