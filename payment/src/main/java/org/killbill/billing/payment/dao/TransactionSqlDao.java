/*
 * Copyright 2014 Groupon, Inc
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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.killbill.billing.util.tag.dao.UUIDCollectionBinder;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@EntitySqlDaoStringTemplate
public interface TransactionSqlDao extends EntitySqlDao<PaymentTransactionModelDao, DirectPaymentTransaction> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateTransactionStatus(@Bind("id") final String transactionId,
                                 @Bind("processedAmount") final BigDecimal processedAmount,
                                 @Bind("processedCurrency") final String processedCurrency,
                                 @Bind("transactionStatus") final String transactionStatus,
                                 @Bind("gatewayErrorCode") final String gatewayErrorCode,
                                 @Bind("gatewayErrorMsg") final String gatewayErrorMsg,
                                 @BindBean final InternalCallContext context);

    @SqlQuery
    List<PaymentTransactionModelDao> getPaymentTransactionsByExternalKey(@Bind("transactionExternalKey") final String transactionExternalKey,
                                                                         @BindBean final InternalTenantContext context);

    @SqlQuery
    List<PaymentTransactionModelDao> getByTransactionStatusPriorDate(@Bind("transactionStatus") final String transactionStatus,
                                                                     @Bind("beforeCreatedDate") final Date beforeCreatedDate,
                                                                     @BindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void failOldPendingTransactions(@UUIDCollectionBinder final Collection<String> pendingTransactionIds,
                                    @Bind("newTransactionStatus") final String newTransactionStatus,
                                    @BindBean final InternalTenantContext context);

    @SqlQuery
    public List<PaymentTransactionModelDao> getByPaymentId(@Bind("paymentId") final UUID paymentId,
                                                           @BindBean final InternalTenantContext context);
}


