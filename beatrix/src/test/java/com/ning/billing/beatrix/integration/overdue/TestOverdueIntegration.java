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

package com.ning.billing.beatrix.integration.overdue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.integration.BeatrixModule;
import com.ning.billing.beatrix.integration.TestIntegrationBase;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedItemCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.XMLLoader;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test(groups = "slow")
@Guice(modules = {BeatrixModule.class})
public class TestOverdueIntegration extends TestIntegrationBase {

    @Inject
    private ClockMock clock;

    @Named("yoyo")
    @Inject
    private
    MockPaymentProviderPlugin paymentPlugin;

    @Inject
    private BlockingApi blockingApi;

    @Inject
    private OverdueWrapperFactory overdueWrapperFactory;

    @Inject
    private OverdueUserApi overdueApi;

    @Inject
    private PaymentApi paymentApi;

    @Inject
    private InvoiceUserApi invoiceApi;

    private Account account;
    private SubscriptionBundle bundle;
    private String productName;
    private BillingPeriod term;

    final PaymentMethodPlugin paymentMethodPlugin = new PaymentMethodPlugin() {
        @Override
        public boolean isDefaultPaymentMethod() {
            return false;
        }

        @Override
        public String getValueString(final String key) {
            return null;
        }

        @Override
        public List<PaymentMethodKVInfo> getProperties() {
            return null;
        }

        @Override
        public String getExternalPaymentMethodId() {
            return UUID.randomUUID().toString();
        }
    };

    @BeforeMethod(groups = "slow")
    public void setupOverdue() throws Exception {
        final String configXml = "<overdueConfig>" +
                                 "   <bundleOverdueStates>" +
                                 "       <state name=\"OD3\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>50</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD3</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "       <state name=\"OD2\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>40</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD2</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>30</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </bundleOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final OverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        account = createAccountWithPaymentMethod(getAccountData(0));
        assertNotNull(account);

        paymentApi.addPaymentMethod(BeatrixModule.PLUGIN_NAME, account, true, paymentMethodPlugin, context);

        bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;

        // create account
        // set mock payments to fail
        // reset clock
        // configure basic OD state rules for 2 states OD1 1-2month, OD2 2-3 month
    }

    // We set the the property killbill.payment.retry.days=8,8,8,8,8,8,8,8 so that Payment retry logic does not end with an ABORTED state
    // preventing final instant payment to succeed.
    @Test(groups = "slow")
    public void testBasicOverdueState() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        paymentPlugin.makeAllInvoicesFailWithError(true);

        // Set next invoice to fail and create subscription
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1));

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30));

        // Should still be in clear state
        checkODState(BlockingApi.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15, NextEvent.PAYMENT_ERROR);

        // Should still be in clear state
        checkODState(BlockingApi.CLEAR_STATE_NAME);

        // Note: we have two tracks of payment retries because of the invoice generated at the phase change

        // DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31));

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 67 - 37 days after invoice
        addDaysAndCheckForCompletion(2);

        // Should still be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 75 - 45 days after invoice
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        // Should now be in OD2
        checkODState("OD2");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 85 - 55 days after invoice
        addDaysAndCheckForCompletion(10, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        // Should now be in OD3
        checkODState("OD3");
        checkChangePlanWithOverdueState(baseSubscription, true);

        paymentPlugin.makeAllInvoicesFailWithError(false);
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday());
        for (final Invoice invoice : invoices) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                createPaymentAndCheckForCompletion(account, invoice, NextEvent.PAYMENT);
            }
        }

        checkODState(BlockingApi.CLEAR_STATE_NAME);
        checkChangePlanWithOverdueState(baseSubscription, false);

        invoiceChecker.checkRepairedInvoice(account.getId(), 3,
                                            new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                            // We paid up to 07-31, hence the adjustment
                                            new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")),
                                            new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 25), InvoiceItemType.CBA_ADJ, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(account.getId(), 4,
                                    // Note the end date here is not 07-25, but 07-15. The overdue configuration disabled invoicing between 07-15 and 07-25 (e.g. the bundle
                                    // was inaccessible, hence we didn't want to charge the customer for that period, even though the account was overdue).
                                    new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("124.98")),
                                    // Item for the upgraded recurring plan
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("116.09")),
                                    // Credits consumed
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 25), InvoiceItemType.CBA_ADJ, new BigDecimal("-241.07")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31));

        // Verify the account balance: 249.95 - 124.98 - 116.09
        assertEquals(invoiceUserApi.getAccountBalance(account.getId()).compareTo(new BigDecimal("-8.88")), 0);
    }

    @Test(groups = "slow")
    public void testOverdueStateIfNoPaymentMethod() throws Exception {
        // This test is similar to the previous one - but there is no default payment method on the account, so there
        // won't be any payment retry

        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Make sure the account doesn't have any payment method
        accountUserApi.removePaymentMethod(account.getId(), context);

        // Create subscription
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1));

        // DAY 30 have to get out of trial before first payment. A payment error, one for each invoice, should be on the bus (because there is no payment method)
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30));

        // Should still be in clear state
        checkODState(BlockingApi.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15);

        // Should still be in clear state
        checkODState(BlockingApi.CLEAR_STATE_NAME);

        // DAY 65 - 35 days after invoice
        // Single PAYMENT_ERROR here here triggered by the invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31));

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 67 - 37 days after invoice
        addDaysAndCheckForCompletion(2);

        // Should still be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 75 - 45 days after invoice
        addDaysAndCheckForCompletion(8);

        // Should now be in OD2
        checkODState("OD2");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 85 - 55 days after invoice
        addDaysAndCheckForCompletion(10);

        // Should now be in OD3
        checkODState("OD3");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // Add a payment method and set it as default
        paymentApi.addPaymentMethod(BeatrixModule.PLUGIN_NAME, account, true, paymentMethodPlugin, context);

        // Pay all invoices
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday());
        for (final Invoice invoice : invoices) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                createPaymentAndCheckForCompletion(account, invoice, NextEvent.PAYMENT);
            }
        }

        checkODState(BlockingApi.CLEAR_STATE_NAME);
        checkChangePlanWithOverdueState(baseSubscription, false);

        invoiceChecker.checkRepairedInvoice(account.getId(), 3,
                                            new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                            // We paid up to 07-31, hence the adjustment
                                            new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")),
                                            new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 25), InvoiceItemType.CBA_ADJ, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(account.getId(), 4,
                                    // Note the end date here is not 07-25, but 07-15. The overdue configuration disabled invoicing between 07-15 and 07-25 (e.g. the bundle
                                    // was inaccessible, hence we didn't want to charge the customer for that period, even though the account was overdue).
                                    new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("124.98")),
                                    // Item for the upgraded recurring plan
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("116.09")),
                                    // Credits consumed
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 25), InvoiceItemType.CBA_ADJ, new BigDecimal("-241.07")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31));

        // Verify the account balance: 249.95 - 124.98 - 116.09
        assertEquals(invoiceUserApi.getAccountBalance(account.getId()).compareTo(new BigDecimal("-8.88")), 0);
    }

    private void checkChangePlanWithOverdueState(final Subscription subscription, final boolean shouldFail) {
        if (shouldFail) {
            try {
                subscription.changePlan("Pistol", term, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), context);
            } catch (EntitlementUserApiException e) {
                assertTrue(e.getCause() instanceof BlockingApiException);
            }
        } else {
            // Upgrade - we don't expect a payment here due to the scenario (the account will have some CBA)
            changeSubscriptionAndCheckForCompletion(subscription, "Assault-Rifle", BillingPeriod.MONTHLY, NextEvent.CHANGE, NextEvent.INVOICE);
        }
    }

    private void checkODState(final String expected) {
        try {
            // This will test the overdue notification queue: when we move the clock, the overdue system
            // should get notified to refresh its state.
            // Calling explicitly refresh here (overdueApi.refreshOverdueStateFor(bundle)) would not fully
            // test overdue.
            // Since we're relying on the notification queue, we may need to wait a bit (hence await()).
            await().atMost(10, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return expected.equals(blockingApi.getBlockingStateFor(bundle).getStateName());
                }
            });
        } catch (Exception e) {
            Assert.assertEquals(blockingApi.getBlockingStateFor(bundle).getStateName(), expected, "Got exception: " + e.toString());
        }
    }
}
