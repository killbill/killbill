/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.generator;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.MockInternationalPrice;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


public class TestInvoiceWithMetadata extends InvoiceTestSuiteNoDB {


    private Account account;
    private SubscriptionBase subscription;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        super.beforeMethod();

        try {
            account = invoiceUtil.createAccount(callContext);
            subscription = invoiceUtil.createSubscription();
        } catch (final Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "fast")
    public void testWith$0RecurringItem() {


        final LocalDate invoiceDate = new LocalDate(2016, 11, 15);

        final DefaultInvoice originalInvoice = new DefaultInvoice(account.getId(), invoiceDate, account.getCurrency());

        final Plan plan = new MockPlan("my-plan");
        final MockInternationalPrice price = new MockInternationalPrice(new DefaultPrice(BigDecimal.TEN, account.getCurrency()));

        final PlanPhase planPhase = new MockPlanPhase(price, null, BillingPeriod.MONTHLY, PhaseType.EVERGREEN);

        final BillingEvent event = invoiceUtil.createMockBillingEvent(account,
                                                                      subscription,
                                                                      invoiceDate.toDateTimeAtStartOfDay(),
                                                                      plan,
                                                                      planPhase,
                                                                      null,
                                                                      BigDecimal.ZERO,
                                                                      account.getCurrency(),
                                                                      planPhase.getRecurring().getBillingPeriod(),
                                                                      1,
                                                                      BillingMode.IN_ADVANCE,
                                                                      "Billing Event Desc",
                                                                      1L,
                                                                      SubscriptionBaseTransitionType.CREATE);


        final InvoiceItem invoiceItem  = new RecurringInvoiceItem(UUID.randomUUID(),
                                                                  invoiceDate.toDateTimeAtStartOfDay(),
                                                                  originalInvoice.getId(),
                                                                  account.getId(),
                                                                  subscription.getBundleId(),
                                                                  subscription.getId(),
                                                                  null,
                                                                  event.getPlan().getName(),
                                                                  event.getPlanPhase().getName(),
                                                                  invoiceDate,
                                                                  invoiceDate.plusMonths(1),
                                                                  BigDecimal.ZERO,
                                                                  BigDecimal.ZERO,
                                                                  account.getCurrency());

        originalInvoice.addInvoiceItem(invoiceItem);

        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<UUID, SubscriptionFutureNotificationDates>();
        final SubscriptionFutureNotificationDates subscriptionFutureNotificationDates = new SubscriptionFutureNotificationDates(BillingMode.IN_ADVANCE);
        subscriptionFutureNotificationDates.updateNextRecurringDateIfRequired(invoiceDate.plusMonths(1));

        perSubscriptionFutureNotificationDates.put(subscription.getId(), subscriptionFutureNotificationDates);

        final InvoiceWithMetadata invoiceWithMetadata = new InvoiceWithMetadata(originalInvoice, perSubscriptionFutureNotificationDates);

        // We generate an invoice with one item, invoicing for $0
        final Invoice resultingInvoice = invoiceWithMetadata.getInvoice();
        Assert.assertNotNull(resultingInvoice);
        Assert.assertEquals(resultingInvoice.getInvoiceItems().size(), 1);
        Assert.assertEquals(resultingInvoice.getInvoiceItems().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);

        final Map<UUID, InvoiceWithMetadata.SubscriptionFutureNotificationDates> dateMap = invoiceWithMetadata.getPerSubscriptionFutureNotificationDates();

        final InvoiceWithMetadata.SubscriptionFutureNotificationDates futureNotificationDates = dateMap.get(subscription.getId());

        // We verify that we generated the future notification for a month ahead
        Assert.assertNotNull(futureNotificationDates.getNextRecurringDate());
        Assert.assertEquals(futureNotificationDates.getNextRecurringDate().compareTo(invoiceDate.plusMonths(1)), 0 );
    }

}
