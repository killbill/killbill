/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.osgi.bundles.analytics.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

import com.ning.billing.commons.jdbi.binder.SmartBindBean;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessTagModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface BusinessAnalyticsSqlDao extends Transactional<BusinessAnalyticsSqlDao> {

    @SqlUpdate
    public void create(final String tableName,
                       @SmartBindBean final BusinessModelDaoBase entity,
                       @SmartBindBean final CallContext callContext);

    @SqlUpdate
    public void deleteByAccountRecordId(@Bind("tableName") final String tableName,
                                        @Bind("accountRecordId") final Long accountRecordId,
                                        @Bind("tenantRecordId") final Long tenantRecordId,
                                        @SmartBindBean final CallContext callContext);

    @SqlQuery
    public BusinessAccountModelDao getAccountByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                               @Bind("tenantRecordId") final Long tenantRecordId,
                                                               @SmartBindBean final TenantContext tenantContext);

    @SqlQuery
    public List<BusinessSubscriptionTransitionModelDao> getSubscriptionTransitionsByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                                                                    @Bind("tenantRecordId") final Long tenantRecordId,
                                                                                                    @SmartBindBean final TenantContext tenantContext);

    @SqlQuery
    public List<BusinessOverdueStatusModelDao> getOverdueStatusesByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                                                   @Bind("tenantRecordId") final Long tenantRecordId,
                                                                                   @SmartBindBean final TenantContext tenantContext);

    @SqlQuery
    public List<BusinessInvoiceModelDao> getInvoicesByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                                      @Bind("tenantRecordId") final Long tenantRecordId,
                                                                      @SmartBindBean final TenantContext tenantContext);

    @SqlQuery
    public List<BusinessInvoiceItemBaseModelDao> getInvoiceItemsByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                                                  @Bind("tenantRecordId") final Long tenantRecordId,
                                                                                  @SmartBindBean final TenantContext tenantContext);

    @SqlQuery
    public List<BusinessInvoicePaymentBaseModelDao> getInvoicePaymentsByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                                                        @Bind("tenantRecordId") final Long tenantRecordId,
                                                                                        @SmartBindBean final TenantContext tenantContext);

    @SqlQuery
    public List<BusinessFieldModelDao> getFieldsByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                                  @Bind("tenantRecordId") final Long tenantRecordId,
                                                                  @SmartBindBean final TenantContext tenantContext);

    @SqlQuery
    public List<BusinessTagModelDao> getTagsByAccountRecordId(@Bind("accountRecordId") final Long accountRecordId,
                                                              @Bind("tenantRecordId") final Long tenantRecordId,
                                                              @SmartBindBean final TenantContext tenantContext);
}
