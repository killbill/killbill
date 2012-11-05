/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.dao;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import com.ning.billing.util.entity.dao.UpdatableEntitySqlDao;

@EntitySqlDaoStringTemplate
@RegisterMapper(PaymentSqlDao.PaymentModelDaoMapper.class)
public interface PaymentSqlDao extends UpdatableEntitySqlDao<PaymentModelDao> {

    @SqlUpdate
    @Audited(ChangeType.INSERT)
    void insertPayment(@Bind(binder = PaymentModelDaoBinder.class) final PaymentModelDao paymentInfo,
                       @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updatePaymentStatusAndExtRef(@Bind("id") final String paymentId,
                                      @Bind("paymentStatus") final String paymentStatus,
                                      @Bind("extFirstPaymentRefId") final String extFirstPaymentRefId,
                                      @Bind("extSecondPaymentRefId") final String extSecondPaymentRefId,
                                      @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updatePaymentAmount(@Bind("id") final String paymentId,
                             @Bind("amount") final BigDecimal amount,
                             @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    PaymentModelDao getPayment(@Bind("id") final String paymentId,
                               @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    PaymentModelDao getLastPaymentForAccountAndPaymentMethod(@Bind("accountId") final String accountId,
                                                             @Bind("paymentMethodId") final String paymentMethodId,
                                                             @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<PaymentModelDao> getPaymentsForInvoice(@Bind("invoiceId") final String invoiceId,
                                                @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<PaymentModelDao> getPaymentsForAccount(@Bind("accountId") final String accountId,
                                                @InternalTenantContextBinder final InternalTenantContext context);

    @Override
    @SqlUpdate
    void insertHistoryFromTransaction(@PaymentHistoryBinder final EntityHistory<PaymentModelDao> payment,
                                      @InternalTenantContextBinder final InternalCallContext context);

    public static final class PaymentModelDaoBinder extends BinderBase implements Binder<Bind, PaymentModelDao> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final PaymentModelDao payment) {
            stmt.bind("id", payment.getId().toString());
            stmt.bind("accountId", payment.getAccountId().toString());
            stmt.bind("invoiceId", payment.getInvoiceId().toString());
            stmt.bind("paymentMethodId", payment.getPaymentMethodId().toString());
            stmt.bind("amount", payment.getAmount());
            stmt.bind("currency", payment.getCurrency().toString());
            stmt.bind("effectiveDate", getDate(payment.getEffectiveDate()));
            stmt.bind("paymentStatus", payment.getPaymentStatus().toString());
            stmt.bind("extFirstPaymentRefId", payment.getExtFirstPaymentRefId());
            stmt.bind("extSecondPaymentRefId", payment.getExtSecondPaymentRefId());
        }
    }

    public static class PaymentModelDaoMapper extends MapperBase implements ResultSetMapper<PaymentModelDao> {

        @Override
        public PaymentModelDao map(final int index, final ResultSet rs, final StatementContext ctx)
                throws SQLException {
            final UUID id = getUUID(rs, "id");
            final UUID accountId = getUUID(rs, "account_id");
            final UUID invoiceId = getUUID(rs, "invoice_id");
            final UUID paymentMethodId = getUUID(rs, "payment_method_id");
            final Integer paymentNumber = rs.getInt("payment_number");
            final BigDecimal amount = rs.getBigDecimal("amount");
            final DateTime effectiveDate = getDateTime(rs, "effective_date");
            final Currency currency = Currency.valueOf(rs.getString("currency"));
            final PaymentStatus paymentStatus = PaymentStatus.valueOf(rs.getString("payment_status"));
            final String extFirstPaymentRefId = rs.getString("ext_first_payment_ref_id");
            final String extSecondPaymentRefId = rs.getString("ext_second_payment_ref_id");
            final DateTime createdDate = getDateTime(rs, "created_date");
            final DateTime updatedDate = getDateTime(rs, "updated_date");
            return new PaymentModelDao(id, createdDate, updatedDate, accountId, invoiceId, paymentMethodId, paymentNumber,
                                       amount, currency, paymentStatus, effectiveDate, extFirstPaymentRefId, extSecondPaymentRefId);
        }
    }
}

