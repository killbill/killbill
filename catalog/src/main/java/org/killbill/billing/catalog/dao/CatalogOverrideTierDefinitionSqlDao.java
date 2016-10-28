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

import java.util.List;

@EntitySqlDaoStringTemplate
public interface CatalogOverrideTierDefinitionSqlDao extends Transactional<CatalogOverrideTierDefinitionSqlDao>, CloseMe {

    @SqlUpdate
    public void create(@SmartBindBean final CatalogOverrideTierDefinitionModelDao entity,
                       @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public CatalogOverrideTierDefinitionModelDao getByRecordId(@Bind("recordId") final Long recordId,
                                                               @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<CatalogOverrideTierDefinitionModelDao> getOverriddenUsageTiers(@Bind("targetUsageDefRecordId") Long targetUsageDefRecordId,
                                                                               @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getLastInsertId();
}
