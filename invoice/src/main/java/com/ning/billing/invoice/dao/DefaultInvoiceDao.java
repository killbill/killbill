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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.tag.ControlTagType;

public class DefaultInvoiceDao implements InvoiceDao {
    private final InvoiceSqlDao invoiceSqlDao;
    private final InvoicePaymentSqlDao invoicePaymentSqlDao;
    private final TagUserApi tagUserApi;
    private final NextBillingDatePoster nextBillingDatePoster;

    private final InvoiceItemSqlDao invoiceItemSqlDao;

    @Inject
    public DefaultInvoiceDao(final IDBI dbi,
                             final NextBillingDatePoster nextBillingDatePoster,
                             final TagUserApi tagUserApi) {
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoicePaymentSqlDao = dbi.onDemand(InvoicePaymentSqlDao.class);
        this.invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        this.nextBillingDatePoster = nextBillingDatePoster;
        this.tagUserApi = tagUserApi;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId.toString());
                populateChildren(invoices, invoiceDao);
                return invoices;
            }
        });
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                return getAllInvoicesByAccountFromTransaction(accountId, invoiceDao);
            }
        });
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final DateTime fromDate) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.getInvoicesByAccountAfterDate(accountId.toString(), fromDate.toDate());

                populateChildren(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public List<Invoice> get() {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.get();

                populateChildren(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public Invoice getById(final UUID invoiceId) {
        return invoiceSqlDao.inTransaction(new Transaction<Invoice, InvoiceSqlDao>() {
            @Override
            public Invoice inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final Invoice invoice = invoiceDao.getById(invoiceId.toString());

                if (invoice != null) {
                    populateChildren(invoice, invoiceDao);
                }

                return invoice;
            }
        });
    }

    @Override
    public void create(final Invoice invoice, final CallContext context) {
        invoiceSqlDao.inTransaction(new Transaction<Void, InvoiceSqlDao>() {
            @Override
            public Void inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {

                final Invoice currentInvoice = transactional.getById(invoice.getId().toString());

                if (currentInvoice == null) {
                    final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                    transactional.create(invoice, context);
                    final Long recordId = transactional.getRecordId(invoice.getId().toString());
                    audits.add(new EntityAudit(TableName.INVOICES, recordId, ChangeType.INSERT));

                    List<Long> recordIdList;

                    final List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
                    final InvoiceItemSqlDao transInvoiceItemSqlDao = transactional.become(InvoiceItemSqlDao.class);
                    transInvoiceItemSqlDao.batchCreateFromTransaction(invoiceItems, context);
                    recordIdList = transInvoiceItemSqlDao.getRecordIds(invoice.getId().toString());
                    audits.addAll(createAudits(TableName.INVOICE_ITEMS, recordIdList));

                    List<InvoiceItem> recurringInvoiceItems = invoice.getInvoiceItems(RecurringInvoiceItem.class);

                    notifyOfFutureBillingEvents(transactional, recurringInvoiceItems);

                    final List<InvoicePayment> invoicePayments = invoice.getPayments();
                    final InvoicePaymentSqlDao invoicePaymentSqlDao = transactional.become(InvoicePaymentSqlDao.class);
                    invoicePaymentSqlDao.batchCreateFromTransaction(invoicePayments, context);
                    recordIdList = invoicePaymentSqlDao.getRecordIds(invoice.getId().toString());
                    audits.addAll(createAudits(TableName.INVOICE_PAYMENTS, recordIdList));

                    transactional.insertAuditFromTransaction(audits, context);
                }

                return null;
            }
        });
    }

    private List<EntityAudit> createAudits(final TableName tableName, final List<Long> recordIdList) {
        final List<EntityAudit> entityAuditList = new ArrayList<EntityAudit>();
        for (final Long recordId : recordIdList) {
            entityAuditList.add(new EntityAudit(tableName, recordId, ChangeType.INSERT));
        }

        return entityAuditList;
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(final UUID subscriptionId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString());

                populateChildren(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId) {

        return invoiceSqlDao.inTransaction(new Transaction<BigDecimal, InvoiceSqlDao>() {
            @Override
            public BigDecimal inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {

                BigDecimal accountBalance = BigDecimal.ZERO;
                List<Invoice> invoices = getAllInvoicesByAccountFromTransaction(accountId, transactional);
                List<InvoiceItem> creditBalanceItems = new LinkedList<InvoiceItem>();
                for (Invoice cur : invoices) {
                    accountBalance = accountBalance.add(cur.getBalance());
                    creditBalanceItems.addAll(cur.getInvoiceItems(CreditBalanceAdjInvoiceItem.class));
                }
                BigDecimal cbaTotal = BigDecimal.ZERO;
                for (InvoiceItem cur : creditBalanceItems) {
                    cbaTotal = cbaTotal.add(cur.getAmount());
                }
                return accountBalance.add(cbaTotal.negate());
            }
        });
    }


    @Override
    public void notifyOfPaymentAttempt(final InvoicePayment invoicePayment, final CallContext context) {
        invoicePaymentSqlDao.inTransaction(new Transaction<Void, InvoicePaymentSqlDao>() {
            @Override
            public Void inTransaction(final InvoicePaymentSqlDao transactional, final TransactionStatus status) throws Exception {
                transactional.notifyOfPaymentAttempt(invoicePayment, context);

                final String invoicePaymentId = invoicePayment.getId().toString();
                final Long recordId = transactional.getRecordId(invoicePaymentId);
                final EntityAudit audit = new EntityAudit(TableName.INVOICE_PAYMENTS, recordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);

                return null;
            }
        });
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final DateTime upToDate) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {

                List<Invoice> invoices = getAllInvoicesByAccountFromTransaction(accountId, invoiceDao);
                Collection<Invoice> unpaidInvoices = Collections2.filter(invoices, new Predicate<Invoice>() {
                    @Override
                    public boolean apply(Invoice in) {
                        return (in.getBalance().compareTo(BigDecimal.ZERO) >= 1);
                    }
                });
                return new ArrayList<Invoice>(unpaidInvoices);
            }
        });
    }

    @Override
    public UUID getInvoiceIdByPaymentAttemptId(final UUID paymentAttemptId) {
        return invoiceSqlDao.getInvoiceIdByPaymentAttemptId(paymentAttemptId.toString());
    }

    @Override
    public InvoicePayment getInvoicePayment(final UUID paymentAttemptId) {
        return invoicePaymentSqlDao.getInvoicePayment(paymentAttemptId.toString());
    }

    @Override
    public void setWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException {
        tagUserApi.addTag(invoiceId, ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.toTagDefinition(), context);
    }

    @Override
    public void removeWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException {
        tagUserApi.removeTag(invoiceId, ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.toTagDefinition(), context);
    }

    @Override
    public InvoicePayment postChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException {
        return invoicePaymentSqlDao.inTransaction(new Transaction<InvoicePayment, InvoicePaymentSqlDao>() {
            @Override
            public InvoicePayment inTransaction(final InvoicePaymentSqlDao transactional, final TransactionStatus status) throws Exception {
                final InvoicePayment payment = invoicePaymentSqlDao.getById(invoicePaymentId.toString());
                if (payment == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_NOT_FOUND, invoicePaymentId.toString());
                } else {
                    if (amount.compareTo(BigDecimal.ZERO) < 0) {
                        throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_IS_NEGATIVE);
                    }

                    final InvoicePayment chargeBack = payment.asChargeBack(amount, context.getCreatedDate());
                    invoicePaymentSqlDao.create(chargeBack, context);

                    return chargeBack;
                }
            }
        });
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId) {
        final BigDecimal amount = invoicePaymentSqlDao.getRemainingAmountPaid(invoicePaymentId.toString());
        return amount == null ? BigDecimal.ZERO : amount;
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId) throws InvoiceApiException {
        final UUID accountId = invoicePaymentSqlDao.getAccountIdFromInvoicePaymentId(invoicePaymentId.toString());
        if (accountId == null) {
            throw new InvoiceApiException(ErrorCode.CHARGE_BACK_COULD_NOT_FIND_ACCOUNT_ID, invoicePaymentId);
        } else {
            return accountId;
        }
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId) {
        return invoicePaymentSqlDao.getChargeBacksByAccountId(accountId.toString());
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentAttemptId(final UUID paymentAttemptId) {
        return invoicePaymentSqlDao.getChargebacksByAttemptPaymentId(paymentAttemptId.toString());
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId) throws InvoiceApiException {
        final InvoicePayment chargeback = invoicePaymentSqlDao.getById(chargebackId.toString());
        if (chargeback == null) {
            throw new InvoiceApiException(ErrorCode.CHARGE_BACK_DOES_NOT_EXIST, chargebackId);
        } else {
            return chargeback;
        }
    }

    @Override
    public InvoiceItem getCreditById(final UUID creditId) throws InvoiceApiException {
        return invoiceItemSqlDao.getById(creditId.toString());
    }

    // TODO: make this transactional
    @Override
    public InvoiceItem insertCredit(final UUID accountId, final BigDecimal amount,
                                    final DateTime effectiveDate, final Currency currency,
                                    final CallContext context) {
        final Invoice invoice = new DefaultInvoice(accountId, effectiveDate, effectiveDate, currency);
        invoiceSqlDao.create(invoice, context);

        final InvoiceItem credit = new CreditBalanceAdjInvoiceItem(invoice.getId(), accountId, effectiveDate, amount, currency);
        invoiceItemSqlDao.create(credit, context);

        return credit;
    }

    @Override
    public void test() {
        invoiceSqlDao.test();
    }

    private void populateChildren(final Invoice invoice, final InvoiceSqlDao invoiceSqlDao) {
        getInvoiceItemsWithinTransaction(invoice, invoiceSqlDao);
        getInvoicePaymentsWithinTransaction(invoice, invoiceSqlDao);
    }

    private void populateChildren(final List<Invoice> invoices, final InvoiceSqlDao invoiceSqlDao) {
        getInvoiceItemsWithinTransaction(invoices, invoiceSqlDao);
        getInvoicePaymentsWithinTransaction(invoices, invoiceSqlDao);
    }

    private List<Invoice> getAllInvoicesByAccountFromTransaction(final UUID accountId, final InvoiceSqlDao transactional) {
        final List<Invoice> invoices = transactional.getAllInvoicesByAccount(accountId.toString());
        populateChildren(invoices, transactional);
        return invoices;
    }


    private void getInvoiceItemsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao) {
        for (final Invoice invoice : invoices) {
            getInvoiceItemsWithinTransaction(invoice, invoiceDao);
        }
    }

    private void getInvoiceItemsWithinTransaction(final Invoice invoice, final InvoiceSqlDao transactional) {
        final String invoiceId = invoice.getId().toString();

        final InvoiceItemSqlDao transInvoiceItemSqlDao = transactional.become(InvoiceItemSqlDao.class);
        final List<InvoiceItem> items = transInvoiceItemSqlDao.getInvoiceItemsByInvoice(invoiceId);
        invoice.addInvoiceItems(items);
    }

    private void getInvoicePaymentsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao) {
        for (final Invoice invoice : invoices) {
            getInvoicePaymentsWithinTransaction(invoice, invoiceDao);
        }
    }

    private void getInvoicePaymentsWithinTransaction(final Invoice invoice, final InvoiceSqlDao invoiceSqlDao) {
        final InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceSqlDao.become(InvoicePaymentSqlDao.class);
        final String invoiceId = invoice.getId().toString();
        final List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId);
        invoice.addPayments(invoicePayments);
    }

    private void notifyOfFutureBillingEvents(final InvoiceSqlDao dao, final List<InvoiceItem> invoiceItems) {
        for (final InvoiceItem item : invoiceItems) {
            if (item.getInvoiceItemType() == InvoiceItemType.RECURRING) {
                final RecurringInvoiceItem recurringInvoiceItem = (RecurringInvoiceItem) item;
                if ((recurringInvoiceItem.getEndDate() != null) &&
                        (recurringInvoiceItem.getAmount() == null ||
                                recurringInvoiceItem.getAmount().compareTo(BigDecimal.ZERO) >= 0)) {
                    nextBillingDatePoster.insertNextBillingNotification(dao, item.getSubscriptionId(), recurringInvoiceItem.getEndDate());
                }
            }
        }
    }
}
