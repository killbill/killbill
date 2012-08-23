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
import org.testng.annotations.AfterMethod;
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
                                 "               <unit>DAYS</unit><number>100</number>" + // this number is intentionally too high
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </bundleOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final OverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        account = createAccountWithPaymentMethod(getAccountData(0));
        assertNotNull(account);

        final PaymentMethodPlugin info = new PaymentMethodPlugin() {
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
        paymentApi.addPaymentMethod(BeatrixModule.PLUGIN_NAME, account, true, info, context);

        bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;

        // create account
        // set mock payments to fail
        // reset clock
        // configure basic OD state rules for 2 states OD1 1-2month, OD2 2-3 month
    }

    @AfterMethod
    public void cleanup() {
        // Clear databases
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

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

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

        // Should still be in OD1
        checkODState("OD2");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 85 - 55 days after invoice
        addDaysAndCheckForCompletion(10, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        // Should now be in OD2 state once the update is processed
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
    }

    private void checkChangePlanWithOverdueState(final Subscription subscription, final boolean shouldFail) {
        if (shouldFail) {
            try {
                subscription.changePlan("Pistol", term, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), context);
            } catch (EntitlementUserApiException e) {
                assertTrue(e.getCause() instanceof BlockingApiException);
            }
        } else {
            // Downgrade
            changeSubscriptionAndCheckForCompletion(subscription, "Pistol", BillingPeriod.MONTHLY, NextEvent.CHANGE);
        }
    }

    private void checkODState(final String expected) {

        try {
            await().atMost(10, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    overdueApi.refreshOverdueStateFor(bundle);
                    return expected.equals(blockingApi.getBlockingStateFor(bundle).getStateName());
                }
            });
        } catch (Exception e) {
            Assert.assertEquals(blockingApi.getBlockingStateFor(bundle).getStateName(), expected, "Got exception: " + e.toString());
        }
    }
}
