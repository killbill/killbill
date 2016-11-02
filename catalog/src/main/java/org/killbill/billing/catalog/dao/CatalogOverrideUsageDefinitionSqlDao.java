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

import java.math.BigDecimal;
import java.util.List;

@EntitySqlDaoStringTemplate
public interface CatalogOverrideUsageDefinitionSqlDao extends Transactional<CatalogOverrideUsageDefinitionSqlDao>, CloseMe {

    @SqlUpdate
    public void create(@SmartBindBean final CatalogOverrideUsageDefinitionModelDao entity,
                       @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public CatalogOverrideUsageDefinitionModelDao getByRecordId(@Bind("recordId") final Long recordId,
                                                               @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<CatalogOverrideUsageDefinitionModelDao> getOverriddenPhaseUsages(@Bind("targetPhaseDefRecordId") Long targetPhaseDefRecordId,
                                                                                @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<CatalogOverrideUsageDefinitionModelDao> getByAttributes(@Bind("parentUsageName") String parentPhaseName,
                                                                        @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getLastInsertId();
}