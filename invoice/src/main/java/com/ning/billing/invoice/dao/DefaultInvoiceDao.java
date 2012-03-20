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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.util.CallContext;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
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

    private final Bus eventBus;

	private NextBillingDatePoster nextBillingDatePoster;

    @Inject
    public DefaultInvoiceDao(final IDBI dbi, final Bus eventBus,
                             final EntitlementBillingApi entitlementBillingApi,
                             NextBillingDatePoster nextBillingDatePoster) {
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoicePaymentSqlDao = dbi.onDemand(InvoicePaymentSqlDao.class);
        this.eventBus = eventBus;
        this.entitlementBillingApi = entitlementBillingApi;
        this.nextBillingDatePoster = nextBillingDatePoster;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId.toString());

                getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

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

    			getInvoiceItemsWithinTransaction(invoices, invoiceDao);
    			getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

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

                getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

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

                 getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                 getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

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
                     getInvoiceItemsWithinTransaction(invoice, invoiceDao);
                     getInvoicePaymentsWithinTransaction(invoice, invoiceDao);
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
                    recurringInvoiceItemDao.batchCreateFromTransaction(recurringInvoiceItems);

                    notifyOfFutureBillingEvents(invoiceSqlDao, recurringInvoiceItems);

                    List<InvoiceItem> fixedPriceInvoiceItems = invoice.getInvoiceItems(FixedPriceInvoiceItem.class);
                    FixedPriceInvoiceItemSqlDao fixedPriceInvoiceItemDao = invoiceDao.become(FixedPriceInvoiceItemSqlDao.class);
                    fixedPriceInvoiceItemDao.batchCreateFromTransaction(fixedPriceInvoiceItems, context);

                    setChargedThroughDates(invoiceSqlDao, fixedPriceInvoiceItems, recurringInvoiceItems);

                    // STEPH Why do we need that? Are the payments not always null at this point?
                    List<InvoicePayment> invoicePayments = invoice.getPayments();
                    InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
                    invoicePaymentSqlDao.batchCreateFromTransaction(invoicePayments);
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

    @Override
    public List<Invoice> getInvoicesBySubscription(final UUID subscriptionId) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString());

                getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

                return invoices;
            }
        });
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId) {
        return invoiceSqlDao.getAccountBalance(accountId.toString());
    }

    @Override
    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment) {
        invoicePaymentSqlDao.notifyOfPaymentAttempt(invoicePayment);
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final DateTime upToDate) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                List<Invoice> invoices = invoiceSqlDao.getUnpaidInvoicesByAccountId(accountId.toString(), upToDate.toDate());

                getInvoiceItemsWithinTransaction(invoices, invoiceDao);
                getInvoicePaymentsWithinTransaction(invoices, invoiceDao);

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
    public void test() {
        invoiceSqlDao.test();
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

    private void getInvoicePaymentsWithinTransaction(final Invoice invoice, final InvoiceSqlDao invoiceDao) {
        InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceDao.become(InvoicePaymentSqlDao.class);
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
