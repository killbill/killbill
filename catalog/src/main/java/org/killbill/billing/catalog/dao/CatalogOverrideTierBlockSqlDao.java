package org.killbill.billing.catalog.dao;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;


import java.util.Collection;


@EntitySqlDaoStringTemplate
public interface CatalogOverrideTierBlockSqlDao extends Transactional<CatalogOverrideTierBlockSqlDao>, CloseMe {

    @SqlUpdate
    public void create(@SmartBindBean final CatalogOverrideTierBlockModelDao entity,
                       @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public CatalogOverrideTierBlockModelDao getByRecordId(@Bind("recordId") final Long recordId,
                                                                @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getTargetTierDefinition(@TierBlockKeysCollectionBinder final Collection<String> concatBlockNumAndBlockDefRecordId,
                                        @Bind("targetCount") final Integer targetCount,
                                        @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getLastInsertId();
}
