package com.ning.billing.util.customfield.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.customfield.CustomFieldHistory;
import com.ning.billing.util.customfield.CustomFieldHistoryBinder;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import java.util.List;

@ExternalizedSqlViaStringTemplate3
public interface CustomFieldHistorySqlDao {
    @SqlBatch(transactional=false)
    public void batchAddHistoryFromTransaction(@Bind("objectId") final String objectId,
                                               @Bind("objectType") final String objectType,
                                               @CustomFieldHistoryBinder final List<CustomFieldHistory> entities,
                                               @CallContextBinder final CallContext context);
}
