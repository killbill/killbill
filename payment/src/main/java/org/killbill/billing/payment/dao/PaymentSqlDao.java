/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.dao;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@EntitySqlDaoStringTemplate
public interface PaymentSqlDao extends EntitySqlDao<PaymentModelDao, Payment> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updatePaymentForNewTransaction(@Bind("id") final String paymentId,
                                        @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    Object updatePaymentStateName(@Bind("id") final String paymentId,
                                  @Bind("stateName") final String stateName,
                                  @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    Object updateLastSuccessPaymentStateName(@Bind("id") final String paymentId,
                                             @Bind("stateName") final String stateName,
                                             @Bind("lastSuccessStateName") final String lastSuccessStateName,
                                             @BindBean final InternalCallContext context);

    @SqlQuery
    public PaymentModelDao getPaymentByExternalKey(@Bind("externalKey") final String externalKey,
                                                   @BindBean final InternalTenantContext context);

    @SqlQuery
    public List<PaymentModelDao> getPaymentsByStatesAcrossTenants(@StateCollectionBinder final Collection<String> states,
                                                                  @Bind("createdBeforeDate") final Date createdBeforeDate,
                                                                  @Bind("createdAfterDate") final Date createdAfterDate,
                                                                  @Bind("limit") final int limit);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<PaymentModelDao> searchByState(@PaymentStateCollectionBinder final Collection<String> paymentStates,
                                                   @Bind("offset") final Long offset,
                                                   @Bind("rowCount") final Long rowCount,
                                                   @Define("ordering") final String ordering,
                                                   @BindBean final InternalTenantContext context);

    @SqlQuery
    public Long getSearchByStateCount(@PaymentStateCollectionBinder final Collection<String> paymentStates,
                                      @BindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<PaymentModelDao> getByPluginName(@Bind("pluginName") final String pluginName,
                                                     @Bind("offset") final Long offset,
                                                     @Bind("rowCount") final Long rowCount,
                                                     @Define("ordering") final String ordering,
                                                     @BindBean final InternalTenantContext context);

    @SqlQuery
    public Long getCountByPluginName(@Bind("pluginName") final String pluginName,
                                     @BindBean final InternalTenantContext context);
}
