package com.ning.billing.util.customfield.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.CustomFieldBinder;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import java.util.List;

@ExternalizedSqlViaStringTemplate3
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
