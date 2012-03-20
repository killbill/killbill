package com.ning.billing.util.customfield.dao;

import com.ning.billing.util.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.CustomFieldBinder;
import com.ning.billing.util.entity.CallContextBinder;
import com.ning.billing.util.tag.dao.TagBinder;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

import java.util.List;

public interface CustomFieldAuditSqlDao extends Transactional<CustomFieldAuditSqlDao> {
    @SqlBatch(transactional=false)
    public void batchInsertFromTransaction(@Bind("objectId") final String objectId,
                                           @Bind("objectType") final String objectType,
                                           @CustomFieldBinder final List<CustomField> entities,
                                           @CallContextBinder final CallContext context);

    @SqlBatch(transactional=false)
    public void batchUpdateFromTransaction(@Bind("objectId") final String objectId,
                                           @Bind("objectType") final String objectType,
                                           @CustomFieldBinder final List<CustomField> entities,
                                           @CallContextBinder final CallContext context);

    @SqlBatch(transactional=false)
    public void batchDeleteFromTransaction(@Bind("objectId") final String objectId,
                                           @Bind("objectType") final String objectType,
                                           @CustomFieldBinder final List<CustomField> entities,
                                           @CallContextBinder final CallContext context);
}
