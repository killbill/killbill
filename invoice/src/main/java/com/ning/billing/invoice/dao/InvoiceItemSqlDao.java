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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.CreditAdjInvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.ExternalChargeInvoiceItem;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.ItemAdjInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RefundAdjInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(InvoiceItemSqlDao.InvoiceItemSqlDaoMapper.class)
public interface InvoiceItemSqlDao extends EntitySqlDao<InvoiceItem> {

    @SqlQuery
    List<Long> getRecordIds(@Bind("invoiceId") final String invoiceId,
                            @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId,
                                               @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByAccount(@Bind("accountId") final String accountId,
                                               @BindBean final InternalTenantContext context);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsBySubscription(@Bind("subscriptionId") final String subscriptionId,
                                                    @BindBean final InternalTenantContext context);

    @Override
    @SqlUpdate
    @Audited(ChangeType.INSERT)
    void create(@BindBean final InvoiceItem invoiceItem,
                @BindBean final InternalCallContext context);

    @SqlBatch(transactional = false)
    @Audited(ChangeType.INSERT)
    void batchCreateFromTransaction(@BindBean final List<InvoiceItem> items,
                                    @BindBean final InternalCallContext context);


    public static class InvoiceItemSqlDaoMapper extends MapperBase implements ResultSetMapper<InvoiceItem> {

        @Override
        public InvoiceItem map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
            final UUID id = getUUID(result, "id");
            final InvoiceItemType type = InvoiceItemType.valueOf(result.getString("type"));
            final UUID invoiceId = getUUID(result, "invoice_id");
            final UUID accountId = getUUID(result, "account_id");
            final UUID subscriptionId = getUUID(result, "subscription_id");
            final UUID bundleId = getUUID(result, "bundle_id");
            final String planName = result.getString("plan_name");
            final String phaseName = result.getString("phase_name");
            final LocalDate startDate = getDate(result, "start_date");
            final LocalDate endDate = getDate(result, "end_date");
            final BigDecimal amount = result.getBigDecimal("amount");
            final BigDecimal rate = result.getBigDecimal("rate");
            final Currency currency = Currency.valueOf(result.getString("currency"));
            final UUID linkedItemId = getUUID(result, "linked_item_id");
            final DateTime createdDate = getDateTime(result, "created_date");

            final InvoiceItem item;
            switch (type) {
                case EXTERNAL_CHARGE:
                    item = new ExternalChargeInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, planName, startDate, amount, currency);
                    break;
                case FIXED:
                    item = new FixedPriceInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
                    break;
                case RECURRING:
                    item = new RecurringInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);
                    break;
                case CBA_ADJ:
                    item = new CreditBalanceAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, linkedItemId, amount, currency);
                    break;
                case CREDIT_ADJ:
                    item = new CreditAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, amount, currency);
                    break;
                case REFUND_ADJ:
                    item = new RefundAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, amount, currency);
                    break;
                case REPAIR_ADJ:
                    item = new RepairAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, endDate, amount, currency, linkedItemId);
                    break;
                case ITEM_ADJ:
                    item = new ItemAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, amount, currency, linkedItemId);
                    break;
                default:
                    throw new RuntimeException("Unexpected type of event item " + type);
            }
            return item;
        }
    }
}
