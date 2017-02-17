/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import java.util.Iterator;
import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@EntitySqlDaoStringTemplate
public interface PaymentMethodSqlDao extends EntitySqlDao<PaymentMethodModelDao, PaymentMethod> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void markPaymentMethodAsDeleted(@Bind("id") final String paymentMethodId,
                                    @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void unmarkPaymentMethodAsDeleted(@Bind("id") final String paymentMethodId,
                                      @BindBean final InternalCallContext context);

    @SqlQuery
    PaymentMethodModelDao getByExternalKey(@Bind("externalKey") String paymentMethodExternalKey, @BindBean InternalTenantContext context);

    @SqlQuery
    PaymentMethodModelDao getPaymentMethodByExternalKeyIncludedDeleted(@Bind("externalKey") String paymentMethodExternalKey, @BindBean InternalTenantContext context);

    @SqlQuery
    PaymentMethodModelDao getPaymentMethodIncludedDelete(@Bind("id") final String paymentMethodId,
                                                         @BindBean final InternalTenantContext context);

    @SqlQuery
    List<PaymentMethodModelDao> getForAccount(@BindBean final InternalTenantContext context);

    @SqlQuery
    List<PaymentMethodModelDao> getForAccountIncludedDelete(@BindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<PaymentMethodModelDao> getByPluginName(@Bind("pluginName") final String pluginName,
                                                           @Bind("offset") final Long offset,
                                                           @Bind("rowCount") final Long rowCount,
                                                           @Define("ordering") final String ordering,
                                                           @BindBean final InternalTenantContext context);

    @SqlQuery
    public Long getCountByPluginName(@Bind("pluginName") final String pluginName,
                                     @BindBean final InternalTenantContext context);

}
