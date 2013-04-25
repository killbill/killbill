/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.bundles.analytics.api;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessOverdueStatusModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscription;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionEvent;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessSubscriptionTransitionModelDao;
import com.ning.billing.osgi.bundles.analytics.http.ObjectMapperProvider;

import com.google.common.collect.ImmutableList;

public class TestBusinessSnapshot extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        // Account
        final BusinessAccountModelDao accountModelDao = new BusinessAccountModelDao(account,
                                                                                    accountRecordId,
                                                                                    BigDecimal.ONE,
                                                                                    invoice,
                                                                                    payment,
                                                                                    3,
                                                                                    auditLog,
                                                                                    tenantRecordId,
                                                                                    reportGroup);
        final BusinessAccount businessAccount = new BusinessAccount(accountModelDao);

        // Field
        final BusinessAccountFieldModelDao businessAccountFieldModelDao = new BusinessAccountFieldModelDao(account,
                                                                                                           accountRecordId,
                                                                                                           customField,
                                                                                                           fieldRecordId,
                                                                                                           auditLog,
                                                                                                           tenantRecordId,
                                                                                                           reportGroup);
        final BusinessField businessField = BusinessField.create(businessAccountFieldModelDao);

        // Invoice
        final BusinessInvoiceModelDao invoiceModelDao = new BusinessInvoiceModelDao(account,
                                                                                    accountRecordId,
                                                                                    invoice,
                                                                                    invoiceRecordId,
                                                                                    auditLog,
                                                                                    tenantRecordId,
                                                                                    reportGroup);
        final BusinessInvoiceItemBaseModelDao invoiceItemBaseModelDao = BusinessInvoiceItemBaseModelDao.create(account,
                                                                                                               accountRecordId,
                                                                                                               invoice,
                                                                                                               invoiceItem,
                                                                                                               itemSource,
                                                                                                               invoiceItemType,
                                                                                                               invoiceItemRecordId,
                                                                                                               secondInvoiceItemRecordId,
                                                                                                               bundle,
                                                                                                               plan,
                                                                                                               phase,
                                                                                                               auditLog,
                                                                                                               tenantRecordId,
                                                                                                               reportGroup);
        final BusinessInvoice businessInvoice = new BusinessInvoice(invoiceModelDao,
                                                                    ImmutableList.<BusinessInvoiceItemBaseModelDao>of(invoiceItemBaseModelDao));

        // Invoice payment
        final BusinessInvoicePaymentBaseModelDao invoicePaymentBaseModelDao = BusinessInvoicePaymentModelDao.create(account,
                                                                                                                    accountRecordId,
                                                                                                                    invoice,
                                                                                                                    invoicePayment,
                                                                                                                    invoicePaymentRecordId,
                                                                                                                    payment,
                                                                                                                    refund,
                                                                                                                    paymentMethod,
                                                                                                                    auditLog,
                                                                                                                    tenantRecordId,
                                                                                                                    reportGroup);
        final BusinessInvoicePayment businessInvoicePayment = new BusinessInvoicePayment(invoicePaymentBaseModelDao);

        // Overdue
        final DateTime endDate = new DateTime(2005, 6, 5, 4, 5, 6, DateTimeZone.UTC);
        final BusinessOverdueStatusModelDao businessOverdueStatusModelDao = new BusinessOverdueStatusModelDao(account,
                                                                                                              accountRecordId,
                                                                                                              bundle,
                                                                                                              blockingState,
                                                                                                              blockingStateRecordId,
                                                                                                              endDate,
                                                                                                              auditLog,
                                                                                                              tenantRecordId,
                                                                                                              reportGroup);
        final BusinessOverdueStatus businessOverdueStatus = new BusinessOverdueStatus(businessOverdueStatusModelDao);

        // Subscriptions
        final DateTime startDate = new DateTime(2012, 6, 5, 4, 3, 12, DateTimeZone.UTC);
        final DateTime requestedTimestamp = new DateTime(2012, 7, 21, 10, 10, 10, DateTimeZone.UTC);

        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.valueOf("ADD_BASE");
        final BusinessSubscription previousSubscription = null;
        final BusinessSubscription nextSubscription = new BusinessSubscription(null, null, null, Currency.GBP, startDate, SubscriptionState.ACTIVE);
        final BusinessSubscriptionTransitionModelDao subscriptionTransitionModelDao = new BusinessSubscriptionTransitionModelDao(account,
                                                                                                                                 accountRecordId,
                                                                                                                                 bundle,
                                                                                                                                 subscriptionTransition,
                                                                                                                                 subscriptionEventRecordId,
                                                                                                                                 requestedTimestamp,
                                                                                                                                 event,
                                                                                                                                 previousSubscription,
                                                                                                                                 nextSubscription,
                                                                                                                                 auditLog,
                                                                                                                                 tenantRecordId,
                                                                                                                                 reportGroup);
        final BusinessSubscriptionTransition businessSubscriptionTransition = new BusinessSubscriptionTransition(subscriptionTransitionModelDao);

        // Tag
        final BusinessAccountTagModelDao businessAccountTagModelDao = new BusinessAccountTagModelDao(account,
                                                                                                     accountRecordId,
                                                                                                     tag,
                                                                                                     tagRecordId,
                                                                                                     tagDefinition,
                                                                                                     auditLog,
                                                                                                     tenantRecordId,
                                                                                                     reportGroup);
        final BusinessTag businessTag = BusinessTag.create(businessAccountTagModelDao);

        // Create the snapshot
        final BusinessSnapshot businessSnapshot = new BusinessSnapshot(businessAccount,
                                                                       ImmutableList.<BusinessSubscriptionTransition>of(businessSubscriptionTransition),
                                                                       ImmutableList.<BusinessInvoice>of(businessInvoice),
                                                                       ImmutableList.<BusinessInvoicePayment>of(businessInvoicePayment),
                                                                       ImmutableList.<BusinessOverdueStatus>of(businessOverdueStatus),
                                                                       ImmutableList.<BusinessTag>of(businessTag),
                                                                       ImmutableList.<BusinessField>of(businessField));
        Assert.assertEquals(businessSnapshot.getBusinessAccount(), businessAccount);
        Assert.assertEquals(businessSnapshot.getBusinessSubscriptionTransitions().size(), 1);
        Assert.assertEquals(businessSnapshot.getBusinessSubscriptionTransitions().iterator().next(), businessSubscriptionTransition);
        Assert.assertEquals(businessSnapshot.getBusinessInvoices().size(), 1);
        Assert.assertEquals(businessSnapshot.getBusinessInvoices().iterator().next(), businessInvoice);
        Assert.assertEquals(businessSnapshot.getBusinessInvoicePayments().size(), 1);
        Assert.assertEquals(businessSnapshot.getBusinessInvoicePayments().iterator().next(), businessInvoicePayment);
        Assert.assertEquals(businessSnapshot.getBusinessOverdueStatuses().size(), 1);
        Assert.assertEquals(businessSnapshot.getBusinessOverdueStatuses().iterator().next(), businessOverdueStatus);
        Assert.assertEquals(businessSnapshot.getBusinessTags().size(), 1);
        Assert.assertEquals(businessSnapshot.getBusinessTags().iterator().next(), businessTag);
        Assert.assertEquals(businessSnapshot.getBusinessFields().size(), 1);
        Assert.assertEquals(businessSnapshot.getBusinessFields().iterator().next(), businessField);

        // We check we can write it out without exception - we can't deserialize it back (no annotation)
        // but we don't care since the APIs are read-only for Analytics
        final String asJson = ObjectMapperProvider.get().writeValueAsString(businessSnapshot);
    }
}
