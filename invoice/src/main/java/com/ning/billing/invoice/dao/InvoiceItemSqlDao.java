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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.CreditAdjInvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.ItemAdjInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RefundAdjInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(InvoiceItemSqlDao.InvoiceItemSqlDaoMapper.class)
public interface InvoiceItemSqlDao extends EntitySqlDao<InvoiceItem> {

    @SqlQuery
    List<Long> getRecordIds(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByInvoice(@Bind("invoiceId") final String invoiceId);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsByAccount(@Bind("accountId") final String accountId);

    @SqlQuery
    List<InvoiceItem> getInvoiceItemsBySubscription(@Bind("subscriptionId") final String subscriptionId);

    @Override
    @SqlUpdate
    void create(@InvoiceItemBinder final InvoiceItem invoiceItem, @CallContextBinder final CallContext context);

    @SqlBatch(transactional = false)
    void batchCreateFromTransaction(@InvoiceItemBinder final List<InvoiceItem> items, @CallContextBinder final CallContext context);

    @BindingAnnotation(InvoiceItemBinder.InvoiceItemBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface InvoiceItemBinder {

        public static class InvoiceItemBinderFactory implements BinderFactory {

            @Override
            public Binder build(final Annotation annotation) {
                return new Binder<InvoiceItemBinder, InvoiceItem>() {
                    @Override
                    public void bind(final SQLStatement<?> q, final InvoiceItemBinder bind, final InvoiceItem item) {
                        q.bind("id", item.getId().toString());
                        q.bind("type", item.getInvoiceItemType().toString());
                        q.bind("invoiceId", item.getInvoiceId().toString());
                        q.bind("accountId", item.getAccountId().toString());
                        q.bind("bundleId", item.getBundleId() == null ? null : item.getBundleId().toString());
                        q.bind("subscriptionId", item.getSubscriptionId() == null ? null : item.getSubscriptionId().toString());
                        q.bind("planName", item.getPlanName() == null ? null : item.getPlanName());
                        q.bind("phaseName", item.getPhaseName() == null ? item.getPhaseName() : item.getPhaseName());
                        q.bind("startDate", item.getStartDate().toDate());
                        q.bind("endDate", item.getEndDate() == null ? null : item.getEndDate().toDate());
                        q.bind("amount", item.getAmount());
                        q.bind("rate", (item.getRate() == null) ? null : item.getRate());
                        q.bind("currency", item.getCurrency().toString());
                        q.bind("linkedItemId", (item.getLinkedItemId() == null) ? null : item.getLinkedItemId().toString());
                    }
                };
            }
        }
    }

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

            InvoiceItem item = null;
            switch (type) {
                case FIXED:
                    item = new FixedPriceInvoiceItem(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, amount, currency);
                    break;
                case RECURRING:
                    item = new RecurringInvoiceItem(id, invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, endDate, amount, rate, currency);
                    break;
                case CBA_ADJ:
                    item = new CreditBalanceAdjInvoiceItem(id, invoiceId, accountId, startDate, amount, currency);
                    break;
                case CREDIT_ADJ:
                    item = new CreditAdjInvoiceItem(id, invoiceId, accountId, startDate, amount, currency);
                    break;
                case REFUND_ADJ:
                    item = new RefundAdjInvoiceItem(id, invoiceId, accountId, startDate, amount, currency);
                    break;
                case REPAIR_ADJ:
                    item = new RepairAdjInvoiceItem(id, invoiceId, accountId, startDate, endDate, amount, currency, linkedItemId);
                    break;
                case ITEM_ADJ:
                    item = new ItemAdjInvoiceItem(id, invoiceId, accountId, startDate, amount, currency, linkedItemId);
                    break;
                default:
                    throw new RuntimeException("Unexpected type of event item " + type);
            }
            return item;
        }
    }
}
