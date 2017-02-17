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

package org.killbill.billing.payment.dao;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@EntitySqlDaoStringTemplate
public interface PaymentAttemptSqlDao extends EntitySqlDao<PaymentAttemptModelDao, Entity> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateAttempt(@Bind("id") final String attemptId,
                       @Bind("transactionId") final String transactionId,
                       @Bind("stateName") final String stateName,
                       @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateAttemptWithProperties(@Bind("id") final String attemptId,
                                     @Bind("transactionId") final String transactionId,
                                     @Bind("stateName") final String stateName,
                                     @Bind("pluginProperties") final byte[] pluginProperties,
                                     @BindBean final InternalCallContext context);

    @SqlQuery
    List<PaymentAttemptModelDao> getByTransactionExternalKey(@Bind("transactionExternalKey") final String transactionExternalKey,
                                                             @BindBean final InternalTenantContext context);

    @SqlQuery
    List<PaymentAttemptModelDao> getByPaymentExternalKey(@Bind("paymentExternalKey") final String paymentExternalKey,
                                                         @BindBean final InternalTenantContext context);

    @SqlQuery
    Long getCountByStateNameAcrossTenants(@Bind("stateName") final String stateName,
                                          @Bind("createdBeforeDate") final Date createdBeforeDate);

    @SqlQuery
    Iterator<PaymentAttemptModelDao> getByStateNameAcrossTenants(@Bind("stateName") final String stateName,
                                                                 @Bind("createdBeforeDate") final Date createdBeforeDate,
                                                                 @Bind("offset") final Long offset,
                                                                 @Bind("rowCount") final Long rowCount,
                                                                 @Define("ordering") final String ordering);

}
