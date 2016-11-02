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
public interface CatalogOverrideBlockDefinitionSqlDao extends Transactional<CatalogOverrideBlockDefinitionSqlDao>, CloseMe {

    @SqlUpdate
    public void create(@SmartBindBean final CatalogOverrideBlockDefinitionModelDao entity,
                       @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public CatalogOverrideBlockDefinitionModelDao getByRecordId(@Bind("recordId") final Long recordId,
                                                                @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public CatalogOverrideBlockDefinitionModelDao getByAttributes(@Bind("parentUnitName") final String parentUnitName,
                                                                  @Bind("currency") final String currency,
                                                                  @Bind("price") final BigDecimal price,
                                                                  @Bind("max") final double max,
                                                                  @Bind("size") final double size,
                                                                  @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<CatalogOverrideBlockDefinitionModelDao> getOverriddenTierBlocks(@Bind("targetTierDefRecordId") Long targetTierDefRecordId,
                                                                                @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getLastInsertId();
}
