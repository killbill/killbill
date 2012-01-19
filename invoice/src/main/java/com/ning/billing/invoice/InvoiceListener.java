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

import java.util.List;
import java.util.SortedSet;
import java.util.UUID;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.BillingEventSet;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceItemList;

public class InvoiceListener {
    private final static Logger log = LoggerFactory.getLogger(InvoiceListener.class);

    private final InvoiceGenerator generator;
    private final EntitlementBillingApi entitlementBillingApi;
    private final AccountUserApi accountUserApi;
    private final InvoiceUserApi invoiceUserApi;
    private final InvoiceDao invoiceDao;

    @Inject
    public InvoiceListener(final InvoiceGenerator generator, final AccountUserApi accountUserApi,
                           final EntitlementBillingApi entitlementBillingApi,
                           final InvoiceUserApi invoiceUserApi,
                           final InvoiceDao invoiceDao) {
        this.generator = generator;
        this.entitlementBillingApi = entitlementBillingApi;
        this.accountUserApi = accountUserApi;
        this.invoiceUserApi = invoiceUserApi;
        this.invoiceDao = invoiceDao;
    }

    @Subscribe
    public void handleSubscriptionTransition(final SubscriptionTransition transition) {
        UUID subscriptionId = transition.getSubscriptionId();
        if (subscriptionId == null) {
            log.error("Failed handling entitlement change.", new InvoiceApiException(ErrorCode.INVOICE_INVALID_TRANSITION));
            return;
        }

        UUID accountId = null;
        try {
            accountId = entitlementBillingApi.getAccountIdFromSubscriptionId(subscriptionId);
        } catch (EntitlementBillingApiException e) {
            log.error("Failed handling entitlement change.", e);
            return;
        }

        if (accountId == null) {
            log.error("Failed handling entitlement change.",
                      new InvoiceApiException(ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID, subscriptionId.toString()));
            return;
        }

        Account account = accountUserApi.getAccountById(accountId);
        if (account == null) {
            log.error("Failed handling entitlement change.",
                      new InvoiceApiException(ErrorCode.INVOICE_ACCOUNT_ID_INVALID, accountId.toString()));
            return;
        }

        SortedSet<BillingEvent> events = entitlementBillingApi.getBillingEventsForAccount(accountId);
        BillingEventSet billingEvents = new BillingEventSet();
        billingEvents.addAll(events);

        DateTime targetDate = new DateTime();
        Currency targetCurrency = account.getCurrency();

        List<InvoiceItem> items = invoiceUserApi.getInvoiceItemsByAccount(accountId);
        InvoiceItemList invoiceItemList = new InvoiceItemList(items);
        Invoice invoice = generator.generateInvoice(accountId, billingEvents, invoiceItemList, targetDate, targetCurrency);

        if (invoice != null) {
            if (invoice.getNumberOfItems() > 0) {
                invoiceDao.create(invoice);
            }
        }
    }
}
