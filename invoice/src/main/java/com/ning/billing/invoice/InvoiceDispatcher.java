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

package com.ning.billing.invoice;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.user.DefaultEmptyInvoiceNotification;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.BillingEventSet;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.GlobalLocker.LockerService;
import com.ning.billing.util.globallocker.LockFailedException;

public class InvoiceDispatcher {
	private final static Logger log = LoggerFactory.getLogger(InvoiceDispatcher.class);
    private final static int NB_LOCK_TRY = 5;

    private final InvoiceGenerator generator;
    private final BillingApi billingApi;
    private final AccountUserApi accountUserApi;
    private final InvoiceDao invoiceDao;
    private final GlobalLocker locker;
    private final Bus eventBus;
    private final Clock clock;

    private final boolean VERBOSE_OUTPUT;

    @Inject
    public InvoiceDispatcher(final InvoiceGenerator generator, final AccountUserApi accountUserApi,
                             final BillingApi billingApi,
                             final InvoiceDao invoiceDao,
                             final GlobalLocker locker,
                             final Bus eventBus,
                             final Clock clock) {
        this.generator = generator;
        this.billingApi = billingApi;
        this.accountUserApi = accountUserApi;
        this.invoiceDao = invoiceDao;
        this.locker = locker;
        this.eventBus = eventBus;
        this.clock = clock;

        String verboseOutputValue = System.getProperty("VERBOSE_OUTPUT");
        VERBOSE_OUTPUT = (verboseOutputValue != null) && Boolean.parseBoolean(verboseOutputValue);
    }

    public void processSubscription(final SubscriptionEventTransition transition,
                                    final CallContext context) throws InvoiceApiException {
        UUID subscriptionId = transition.getSubscriptionId();
        DateTime targetDate = transition.getEffectiveTransitionTime();
        log.info("Got subscription transition from InvoiceListener. id: " + subscriptionId.toString() + "; targetDate: " + targetDate.toString());
        log.info("Transition type: " + transition.getTransitionType().toString());
        processSubscription(subscriptionId, targetDate, context);
    }

    public void processSubscription(final UUID subscriptionId, final DateTime targetDate,
                                    final CallContext context) throws InvoiceApiException {
        if (subscriptionId == null) {
            log.error("Failed handling entitlement change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
            return;
        }

        UUID accountId = billingApi.getAccountIdFromSubscriptionId(subscriptionId);
        if (accountId == null) {
            log.error("Failed handling entitlement change.",
                    new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
            return;
        }

        processAccount(accountId, targetDate, false, context);
    }
    
    public Invoice processAccount(final UUID accountId, final DateTime targetDate,
                                  final boolean dryRun, final CallContext context) throws InvoiceApiException {
		GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerService.INVOICE, accountId.toString(), NB_LOCK_TRY);

            return processAccountWithLock(accountId, targetDate, dryRun, context);

        } catch (LockFailedException e) {
            // Not good!
            log.error(String.format("Failed to process invoice for account %s, targetDate %s",
                    accountId.toString(), targetDate), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    private void postEmptyInvoiceEvent(final UUID accountId, final UUID userToken) {
        try {
            BusEvent event = new DefaultEmptyInvoiceNotification(accountId, clock.getUTCNow(), userToken);
            eventBus.post(event);
        } catch (EventBusException e){
            log.error("Failed to post DefaultEmptyInvoiceNotification event for account {} ", accountId, e);
        }
    }
    private Invoice processAccountWithLock(final UUID accountId, final DateTime targetDate,
            final boolean dryRun, final CallContext context) throws InvoiceApiException {
        try {
            Account account = accountUserApi.getAccountById(accountId);
            SortedSet<BillingEvent> events = billingApi.getBillingEventsForAccountAndUpdateAccountBCD(accountId);
            BillingEventSet billingEvents = new BillingEventSet(events);

            Currency targetCurrency = account.getCurrency();

            List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId);
            Invoice invoice = generator.generateInvoice(accountId, billingEvents, invoices, targetDate, targetCurrency);

            if (invoice == null) {
                log.info("Generated null invoice.");
                outputDebugData(events, invoices);
                if (!dryRun) {
                    postEmptyInvoiceEvent(accountId, context.getUserToken());
                }
            } else {
                log.info("Generated invoice {} with {} items.", invoice.getId().toString(), invoice.getNumberOfItems());
                if (VERBOSE_OUTPUT) {
                    log.info("New items");
                    for (InvoiceItem item : invoice.getInvoiceItems()) {
                        log.info(item.toString());
                    }
                }
                outputDebugData(events, invoices);
                if (!dryRun) {
                    invoiceDao.create(invoice, context);
                }
            }
            return invoice;
        } catch(AccountApiException e) {
            log.error("Failed handling entitlement change.",e);
            return null;    

        }

    }

    private void outputDebugData(Collection<BillingEvent> events, Collection<Invoice> invoices) {
        if (VERBOSE_OUTPUT) {
            log.info("Events");
            for (BillingEvent event : events) {
                log.info(event.toString());
            }

            log.info("Existing items");
            for (Invoice invoice : invoices) {
                for (InvoiceItem item : invoice.getInvoiceItems()) {
                    log.info(item.toString());
                }
            }
        }
    }
}
