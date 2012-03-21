package com.ning.billing.util.tag.dao;


import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.tag.Tag;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import java.util.List;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(TagMapper.class)
public interface TagAuditSqlDao extends Transactional<TagAuditSqlDao> {
    @SqlBatch(transactional=false)
    public void batchInsertFromTransaction(@Bind("objectId") final String objectId,
                                           @Bind("objectType") final String objectType,
                                           @TagBinder final List<Tag> entities,
                                           @CallContextBinder final CallContext context);

    @SqlBatch(transactional=false)
    public void batchDeleteFromTransaction(@Bind("objectId") final String objectId,
                                           @Bind("objectType") final String objectType,
                                           @TagBinder final List<Tag> entities,
                                           @CallContextBinder final CallContext context);
}
