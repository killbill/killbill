/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.AnalyticsDao;
import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;

public class BusinessInvoiceRecorder {
    private static final Logger log = LoggerFactory.getLogger(BusinessInvoiceRecorder.class);

    private final AnalyticsDao analyticsDao;
    private final AccountUserApi accountApi;
    private final EntitlementUserApi entitlementApi;
    private final InvoiceUserApi invoiceApi;

    @Inject
    public BusinessInvoiceRecorder(final AnalyticsDao analyticsDao,
                                   final AccountUserApi accountApi,
                                   final EntitlementUserApi entitlementApi,
                                   final InvoiceUserApi invoiceApi) {
        this.analyticsDao = analyticsDao;
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.invoiceApi = invoiceApi;
    }

    public void invoiceCreated(final UUID invoiceId) {
        // Lookup the invoice object
        final Invoice invoice = invoiceApi.getInvoice(invoiceId);
        if (invoice == null) {
            log.warn("Ignoring invoice creation for invoice id {} (invoice does not exist)", invoiceId.toString());
            return;
        }

        // Lookup the associated account
        final String accountKey;
        try {
            final Account account = accountApi.getAccountById(invoice.getAccountId());
            accountKey = account.getExternalKey();
        } catch (AccountApiException e) {
            log.warn("Ignoring invoice creation for invoice id {} and account id {} (account does not exist)",
                     invoice.getId().toString(),
                     invoice.getAccountId().toString());
            return;
        }

        // Create the invoice
        final BusinessInvoice businessInvoice = new BusinessInvoice(accountKey, invoice);

        // Create associated invoice items
        final List<BusinessInvoiceItem> businessInvoiceItems = new ArrayList<BusinessInvoiceItem>();
        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            final BusinessInvoiceItem businessInvoiceItem = createBusinessInvoiceItem(invoiceItem);
            if (businessInvoiceItem != null) {
                businessInvoiceItems.add(businessInvoiceItem);
            }
        }

        // Update the Analytics tables
        analyticsDao.createInvoice(accountKey, businessInvoice, businessInvoiceItems);
    }

    private BusinessInvoiceItem createBusinessInvoiceItem(final InvoiceItem invoiceItem) {
        final String externalKey;
        try {
            final SubscriptionBundle bundle = entitlementApi.getBundleFromId(invoiceItem.getBundleId());
            externalKey = bundle.getKey();
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring invoice item {} for bundle {} (bundle does not exist)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getBundleId().toString());
            return null;
        }

        final Subscription subscription;
        try {
            subscription = entitlementApi.getSubscriptionFromId(invoiceItem.getSubscriptionId());
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring invoice item {} for subscription {} (subscription does not exist)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getSubscriptionId().toString());
            return null;
        }

        final Plan plan = subscription.getCurrentPlan();
        if (plan == null) {
            log.warn("Ignoring invoice item {} for subscription {} (null plan)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getSubscriptionId().toString());
            return null;
        }

        final PlanPhase planPhase = subscription.getCurrentPhase();
        if (planPhase == null) {
            log.warn("Ignoring invoice item {} for subscription {} (null phase)",
                     invoiceItem.getId().toString(),
                     invoiceItem.getSubscriptionId().toString());
            return null;
        }

        return new BusinessInvoiceItem(externalKey, invoiceItem, plan, planPhase);
    }
}
