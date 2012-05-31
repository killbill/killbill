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
import java.util.List;
import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.model.CreditInvoiceItem;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.dao.TagDao;

public class DefaultInvoiceDao implements InvoiceDao {
    private final InvoiceSqlDao invoiceSqlDao;
    private final InvoicePaymentSqlDao invoicePaymentSqlDao;
    private final TagDao tagDao;

	private final NextBillingDatePoster nextBillingDatePoster;

    @Inject
    public DefaultInvoiceDao(final IDBI dbi,
                             final NextBillingDatePoster nextBillingDatePoster,
                             final TagDao tagDao) {
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoicePaymentSqlDao = dbi.onDemand(InvoicePaymentSqlDao.class);
        this.nextBillingDatePoster = nextBillingDatePoster;
        this.tagDao = tagDao;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId.toString());

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
    			List<Invoice> invoices = invoiceDao.getAllInvoicesByAccount(accountId.toString());

                populateChildren(invoices, invoiceDao);

    			return invoices;
    		}
    	});
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final DateTime fromDate) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesByAccountAfterDate(accountId.toString(), fromDate.toDate());

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
                 List<Invoice> invoices = invoiceDao.get();

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
                Invoice invoice = invoiceDao.getById(invoiceId.toString());

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

                // STEPH this seems useless
                Invoice currentInvoice = transactional.getById(invoice.getId().toString());

                if (currentInvoice == null) {
                    List<EntityAudit> audits = new ArrayList<EntityAudit>();

                    transactional.create(invoice, context);
                    Long recordId = transactional.getRecordId(invoice.getId().toString());
                    audits.add(new EntityAudit(TableName.INVOICES, recordId, ChangeType.INSERT));

                    List<Long> recordIdList;

                    List<InvoiceItem> recurringInvoiceItems = invoice.getInvoiceItems(RecurringInvoiceItem.class);
                    RecurringInvoiceItemSqlDao recurringInvoiceItemDao = transactional.become(RecurringInvoiceItemSqlDao.class);
                    recurringInvoiceItemDao.batchCreateFromTransaction(recurringInvoiceItems, context);
                    recordIdList = recurringInvoiceItemDao.getRecordIds(invoice.getId().toString());
                    audits.addAll(createAudits(TableName.RECURRING_INVOICE_ITEMS, recordIdList));

                    notifyOfFutureBillingEvents(transactional, recurringInvoiceItems);

                    List<InvoiceItem> fixedPriceInvoiceItems = invoice.getInvoiceItems(FixedPriceInvoiceItem.class);
                    FixedPriceInvoiceItemSqlDao fixedPriceInvoiceItemDao = transactional.become(FixedPriceInvoiceItemSqlDao.class);
                    fixedPriceInvoiceItemDao.batchCreateFromTransaction(fixedPriceInvoiceItems, context);
                    recordIdList = fixedPriceInvoiceItemDao.getRecordIds(invoice.getId().toString());
                    audits.addAll(createAudits(TableName.FIXED_INVOICE_ITEMS, recordIdList));

                    List<InvoiceItem> creditInvoiceItems = invoice.getInvoiceItems(CreditInvoiceItem.class);
                    CreditInvoiceItemSqlDao creditInvoiceItemSqlDao = transactional.become(CreditInvoiceItemSqlDao.class);
                    creditInvoiceItemSqlDao.batchCreateFromTransaction(creditInvoiceItems, context);
                    recordIdList = creditInvoiceItemSqlDao.getRecordIds(invoice.getId().toString());
                    audits.addAll(createAudits(TableName.CREDIT_INVOICE_ITEMS, recordIdList));

                    List<InvoicePayment> invoicePayments = invoice.getPayments();
                    InvoicePaymentSqlDao invoicePaymentSqlDao = transactional.become(InvoicePaymentSqlDao.class);
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
        List<EntityAudit> entityAuditList = new ArrayList<EntityAudit>();
        for (Long recordId : recordIdList) {
            entityAuditList.add(new EntityAudit(tableName, recordId, ChangeType.INSERT));
        }

        return entityAuditList;
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(final UUID subscriptionId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString());

                populateChildren(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId) {
        return invoiceSqlDao.getAccountBalance(accountId.toString());
    }

    @Override
    public void notifyOfPaymentAttempt(final InvoicePayment invoicePayment, final CallContext context) {
        invoicePaymentSqlDao.inTransaction(new Transaction<Void, InvoicePaymentSqlDao>() {
            @Override
            public Void inTransaction(InvoicePaymentSqlDao transactional, TransactionStatus status) throws Exception {
                transactional.notifyOfPaymentAttempt(invoicePayment, context);

                String invoicePaymentId = invoicePayment.getId().toString();
                Long recordId = transactional.getRecordId(invoicePaymentId);
                EntityAudit audit = new EntityAudit(TableName.INVOICE_PAYMENTS, recordId, ChangeType.INSERT);
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
                List<Invoice> invoices = invoiceSqlDao.getUnpaidInvoicesByAccountId(accountId.toString(), upToDate.toDate());

                populateChildren(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public UUID getInvoiceIdByPaymentAttemptId(final UUID paymentAttemptId) {
        return invoiceSqlDao.getInvoiceIdByPaymentAttemptId(paymentAttemptId.toString());
    }

    @Override
    public InvoicePayment getInvoicePayment(final UUID paymentAttemptId) {
        return invoicePaymentSqlDao.getInvoicePayment(paymentAttemptId);
    }

    @Override
    public void setWrittenOff(final UUID invoiceId, final CallContext context) {
        tagDao.insertTag(invoiceId, ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.toTagDefinition(), context);
    }

    @Override
    public void removeWrittenOff(final UUID invoiceId, final CallContext context) throws InvoiceApiException {
        tagDao.deleteTag(invoiceId, ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.toTagDefinition(), context);
    }

    @Override
    public void postChargeBack(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException {
        InvoicePayment payment = invoicePaymentSqlDao.getById(invoicePaymentId.toString());
        if (payment == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_NOT_FOUND, invoicePaymentId.toString());
        } else {
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_IS_NEGATIVE);
            }

            InvoicePayment chargeBack = payment.asChargeBack(amount, context.getCreatedDate());
            invoicePaymentSqlDao.create(chargeBack, context);
        }
    }

    @Override
    public BigDecimal getRemainingAmountPaid(UUID invoicePaymentId) {
        return invoicePaymentSqlDao.getRemainingAmountPaid(invoicePaymentId.toString());
    }

    @Override
    public void test() {
        invoiceSqlDao.test();
    }

    private void populateChildren(final Invoice invoice, InvoiceSqlDao invoiceSqlDao) {
        getInvoiceItemsWithinTransaction(invoice, invoiceSqlDao);
        getInvoicePaymentsWithinTransaction(invoice, invoiceSqlDao);
    }

    private void populateChildren(List<Invoice> invoices, InvoiceSqlDao invoiceSqlDao) {
        getInvoiceItemsWithinTransaction(invoices, invoiceSqlDao);
        getInvoicePaymentsWithinTransaction(invoices, invoiceSqlDao);
    }

    private void getInvoiceItemsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao) {
        for (final Invoice invoice : invoices) {
            getInvoiceItemsWithinTransaction(invoice, invoiceDao);
        }
    }

    private void getInvoiceItemsWithinTransaction(final Invoice invoice, final InvoiceSqlDao invoiceDao) {
        String invoiceId = invoice.getId().toString();

        RecurringInvoiceItemSqlDao recurringInvoiceItemDao = invoiceDao.become(RecurringInvoiceItemSqlDao.class);
        List<InvoiceItem> recurringInvoiceItems = recurringInvoiceItemDao.getInvoiceItemsByInvoice(invoiceId);
        invoice.addInvoiceItems(recurringInvoiceItems);

        FixedPriceInvoiceItemSqlDao fixedPriceInvoiceItemDao = invoiceDao.become(FixedPriceInvoiceItemSqlDao.class);
        List<InvoiceItem> fixedPriceInvoiceItems = fixedPriceInvoiceItemDao.getInvoiceItemsByInvoice(invoiceId);
        invoice.addInvoiceItems(fixedPriceInvoiceItems);

        CreditInvoiceItemSqlDao creditInvoiceItemSqlDao = invoiceDao.become(CreditInvoiceItemSqlDao.class);
        List<InvoiceItem> creditInvoiceItems = creditInvoiceItemSqlDao.getInvoiceItemsByInvoice(invoiceId);
        invoice.addInvoiceItems(creditInvoiceItems);
    }

    private void getInvoicePaymentsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao) {
        for (Invoice invoice : invoices) {
            getInvoicePaymentsWithinTransaction(invoice, invoiceDao);
        }
    }

    private void getInvoicePaymentsWithinTransaction(final Invoice invoice, final InvoiceSqlDao invoiceSqlDao) {
        InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceSqlDao.become(InvoicePaymentSqlDao.class);
        String invoiceId = invoice.getId().toString();
        List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId);
        invoice.addPayments(invoicePayments);
    }

    private void notifyOfFutureBillingEvents(final InvoiceSqlDao dao, final List<InvoiceItem> invoiceItems) {
        for (final InvoiceItem item : invoiceItems) {
            if (item instanceof RecurringInvoiceItem) {
                RecurringInvoiceItem recurringInvoiceItem = (RecurringInvoiceItem) item;
                if ((recurringInvoiceItem.getEndDate() != null) &&
                        (recurringInvoiceItem.getAmount() == null ||
                                recurringInvoiceItem.getAmount().compareTo(BigDecimal.ZERO) >= 0)) {
                	nextBillingDatePoster.insertNextBillingNotification(dao, item.getSubscriptionId(), recurringInvoiceItem.getEndDate());
                }
            }
        }
    }
}
