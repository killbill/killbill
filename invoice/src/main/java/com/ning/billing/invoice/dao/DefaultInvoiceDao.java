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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.audit.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.dao.CustomFieldSqlDao;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.dao.TagDao;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.bus.Bus;

public class DefaultInvoiceDao implements InvoiceDao {
    private final static Logger log = LoggerFactory.getLogger(DefaultInvoiceDao.class);

    private final InvoiceSqlDao invoiceSqlDao;
    private final InvoicePaymentSqlDao invoicePaymentSqlDao;
    private final EntitlementBillingApi entitlementBillingApi;
    private final TagDao tagDao;

    private final Bus eventBus;

	private final NextBillingDatePoster nextBillingDatePoster;

    @Inject
    public DefaultInvoiceDao(final IDBI dbi, final Bus eventBus,
                             final EntitlementBillingApi entitlementBillingApi,
                             final NextBillingDatePoster nextBillingDatePoster,
                             final TagDao tagDao) {
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoicePaymentSqlDao = dbi.onDemand(InvoicePaymentSqlDao.class);
        this.eventBus = eventBus;
        this.entitlementBillingApi = entitlementBillingApi;
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
            public Void inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {

                // STEPH this seems useless
                Invoice currentInvoice = invoiceDao.getById(invoice.getId().toString());

                if (currentInvoice == null) {
                    invoiceDao.create(invoice, context);

                    List<InvoiceItem> recurringInvoiceItems = invoice.getInvoiceItems(RecurringInvoiceItem.class);
                    RecurringInvoiceItemSqlDao recurringInvoiceItemDao = invoiceDao.become(RecurringInvoiceItemSqlDao.class);
                    recurringInvoiceItemDao.batchCreateFromTransaction(recurringInvoiceItems, context);

                    notifyOfFutureBillingEvents(invoiceSqlDao, recurringInvoiceItems);

                    List<InvoiceItem> fixedPriceInvoiceItems = invoice.getInvoiceItems(FixedPriceInvoiceItem.class);
                    FixedPriceInvoiceItemSqlDao fixedPriceInvoiceItemDao = invoiceDao.become(FixedPriceInvoiceItemSqlDao.class);
                    fixedPriceInvoiceItemDao.batchCreateFromTransaction(fixedPriceInvoiceItems, context);

                    setChargedThroughDates(invoiceSqlDao, fixedPriceInvoiceItems, recurringInvoiceItems);

                    // STEPH Why do we need that? Are the payments not always null at this point?
                    List<InvoicePayment> invoicePayments = invoice.getPayments();
                    InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                    invoicePaymentSqlDao.batchCreateFromTransaction(invoicePayments, context);

                    AuditSqlDao auditSqlDao = invoiceDao.become(AuditSqlDao.class);
                    auditSqlDao.insertAuditFromTransaction("invoices", invoice.getId().toString(), ChangeType.INSERT, context);
                    auditSqlDao.insertAuditFromTransaction("recurring_invoice_items", getIdsFromInvoiceItems(recurringInvoiceItems), ChangeType.INSERT, context);
                    auditSqlDao.insertAuditFromTransaction("fixed_invoice_items", getIdsFromInvoiceItems(fixedPriceInvoiceItems), ChangeType.INSERT, context);
                    auditSqlDao.insertAuditFromTransaction("invoice_payments", getIdsFromInvoicePayments(invoicePayments), ChangeType.INSERT, context);

                }

                return null;
            }
        });

        // TODO: move this inside the transaction once the bus is persistent
        InvoiceCreationNotification event;
        event = new DefaultInvoiceCreationNotification(invoice.getId(), invoice.getAccountId(),
                                                      invoice.getBalance(), invoice.getCurrency(),
                                                      invoice.getInvoiceDate());
        try {
            eventBus.post(event);
        } catch (Bus.EventBusException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getIdsFromInvoiceItems(List<InvoiceItem> invoiceItems) {
        List<String> ids = new ArrayList<String>();

        for (InvoiceItem item : invoiceItems) {
            ids.add(item.getId().toString());
        }

        return ids;
    }

    private List<String> getIdsFromInvoicePayments(List<InvoicePayment> invoicePayments) {
        List<String> ids = new ArrayList<String>();

        for (InvoicePayment payment : invoicePayments) {
            ids.add(payment.getId().toString());
        }

        return ids;
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

                AuditSqlDao auditSqlDao = transactional.become(AuditSqlDao.class);
                String invoicePaymentId = invoicePayment.getId().toString();
                auditSqlDao.insertAuditFromTransaction("invoice_payments", invoicePaymentId, ChangeType.INSERT, context);

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
    public UUID getInvoiceIdByPaymentAttemptId(UUID paymentAttemptId) {
        return invoiceSqlDao.getInvoiceIdByPaymentAttemptId(paymentAttemptId.toString());
    }

    @Override
    public InvoicePayment getInvoicePayment(UUID paymentAttemptId) {
        return invoicePaymentSqlDao.getInvoicePayment(paymentAttemptId);
    }

    @Override
    public void addControlTag(ControlTagType controlTagType, UUID invoiceId, CallContext context) {
        tagDao.addTag(controlTagType.toString(), invoiceId, Invoice.ObjectType, context);
    }

    @Override
    public void removeControlTag(ControlTagType controlTagType, UUID invoiceId, CallContext context) {
        tagDao.removeTag(controlTagType.toString(), invoiceId, Invoice.ObjectType, context);
    }

    @Override
    public void test() {
        invoiceSqlDao.test();
    }

    private void populateChildren(final Invoice invoice, InvoiceSqlDao invoiceSqlDao) {
        getInvoiceItemsWithinTransaction(invoice, invoiceSqlDao);
        getInvoicePaymentsWithinTransaction(invoice, invoiceSqlDao);
        getTagsWithinTransaction(invoice, invoiceSqlDao);
        getFieldsWithinTransaction(invoice, invoiceSqlDao);
    }

    private void populateChildren(List<Invoice> invoices, InvoiceSqlDao invoiceSqlDao) {
        getInvoiceItemsWithinTransaction(invoices, invoiceSqlDao);
        getInvoicePaymentsWithinTransaction(invoices, invoiceSqlDao);
        getTagsWithinTransaction(invoices, invoiceSqlDao);
        getFieldsWithinTransaction(invoices, invoiceSqlDao);
    }

    private void getInvoiceItemsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao) {
        for (final Invoice invoice : invoices) {
            getInvoiceItemsWithinTransaction(invoice, invoiceDao);
        }
    }

    private void getInvoiceItemsWithinTransaction(final Invoice invoice, final InvoiceSqlDao invoiceDao) {
        RecurringInvoiceItemSqlDao recurringInvoiceItemDao = invoiceDao.become(RecurringInvoiceItemSqlDao.class);
        List<InvoiceItem> recurringInvoiceItems = recurringInvoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
        invoice.addInvoiceItems(recurringInvoiceItems);

        FixedPriceInvoiceItemSqlDao fixedPriceInvoiceItemDao = invoiceDao.become(FixedPriceInvoiceItemSqlDao.class);
        List<InvoiceItem> fixedPriceInvoiceItems = fixedPriceInvoiceItemDao.getInvoiceItemsByInvoice(invoice.getId().toString());
        invoice.addInvoiceItems(fixedPriceInvoiceItems);
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

    private void getTagsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceSqlDao) {
        for (final Invoice invoice : invoices) {
            getTagsWithinTransaction(invoice, invoiceSqlDao);
        }
    }

    private void getTagsWithinTransaction(final Invoice invoice, final Transmogrifier dao) {
        List<Tag> tags = tagDao.loadTagsFromTransaction(dao, invoice.getId(), Invoice.ObjectType);
        invoice.addTags(tags);
    }

    private void getFieldsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceSqlDao) {
        for (final Invoice invoice : invoices) {
            getFieldsWithinTransaction(invoice, invoiceSqlDao);
        }
    }

    private void getFieldsWithinTransaction(final Invoice invoice, final InvoiceSqlDao invoiceSqlDao) {
        CustomFieldSqlDao customFieldSqlDao = invoiceSqlDao.become(CustomFieldSqlDao.class);
        String invoiceId = invoice.getId().toString();
        List<CustomField> customFields = customFieldSqlDao.load(invoiceId, Invoice.ObjectType);
        invoice.setFields(customFields);
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
    
    private void setChargedThroughDates(final InvoiceSqlDao dao, final Collection<InvoiceItem> fixedPriceItems,
                                        final Collection<InvoiceItem> recurringItems) {
        Map<UUID, DateTime> chargeThroughDates = new HashMap<UUID, DateTime>();
        addInvoiceItemsToChargeThroughDates(chargeThroughDates, fixedPriceItems);
        addInvoiceItemsToChargeThroughDates(chargeThroughDates, recurringItems);

        for (UUID subscriptionId : chargeThroughDates.keySet()) {
            DateTime chargeThroughDate = chargeThroughDates.get(subscriptionId);
            log.info("Setting CTD for subscription {} to {}", subscriptionId.toString(), chargeThroughDate.toString());
            entitlementBillingApi.setChargedThroughDateFromTransaction(dao, subscriptionId, chargeThroughDate);
        }
    }

    private void addInvoiceItemsToChargeThroughDates(Map<UUID, DateTime> chargeThroughDates, Collection<InvoiceItem> items) {
        for (InvoiceItem item : items) {
            UUID subscriptionId = item.getSubscriptionId();
            DateTime endDate = item.getEndDate();

            if (chargeThroughDates.containsKey(subscriptionId)) {
                if (chargeThroughDates.get(subscriptionId).isBefore(endDate)) {
                    chargeThroughDates.put(subscriptionId, endDate);
                }
            } else {
                chargeThroughDates.put(subscriptionId, endDate);
            }
        }
    }
}
