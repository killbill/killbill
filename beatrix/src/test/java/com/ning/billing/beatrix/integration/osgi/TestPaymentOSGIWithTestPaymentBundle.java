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

package com.ning.billing.beatrix.integration.osgi;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.integration.BeatrixIntegrationModule;
import com.ning.billing.beatrix.osgi.SetupBundleWithAssertion;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import com.ning.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.DefaultEntitlement;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentPluginApiWithTestControl;

public class TestPaymentOSGIWithTestPaymentBundle extends TestOSGIBase {

    private final String BUNDLE_TEST_RESOURCE = "killbill-osgi-bundles-test-payment";

    @Inject
    private OSGIServiceRegistration<PaymentPluginApi> paymentPluginApiOSGIServiceRegistration;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {

        super.beforeClass();

        // This is extracted from surefire system configuration-- needs to be added explicitly in IntelliJ for correct running
        final String killbillVersion = System.getProperty("killbill.version");
        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion(BUNDLE_TEST_RESOURCE, osgiConfig, killbillVersion);
        setupTest.setupJavaBundle();

    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        getTestPluginPaymentApi().resetToNormalbehavior();
    }

    @Test(groups = "slow")
    public void testBasicProcessPaymentOK() throws Exception {
        final PaymentPluginApiWithTestControl paymentPluginApi = getTestPluginPaymentApi();
        paymentPluginApi.processPayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Currency.USD, callContext);
    }

    @Test(groups = "slow")
    public void testBasicProcessPaymentWithPaymentPluginApiException() throws Exception {

        boolean gotException = false;
        try {
            final PaymentPluginApiWithTestControl paymentPluginApi = getTestPluginPaymentApi();
            final PaymentPluginApiException e = new PaymentPluginApiException("test-error", "foo");

            paymentPluginApi.setPaymentPluginApiExceptionOnNextCalls(e);
            paymentPluginApi.processPayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Currency.USD, callContext);
            Assert.fail("Expected to fail with " + e.toString());
        } catch (PaymentPluginApiException e) {
            gotException = true;
        }
        Assert.assertTrue(gotException);
    }

    @Test(groups = "slow")
    public void testBasicProcessPaymentWithRuntimeException() throws Exception {

        boolean gotException = false;
        try {
            final PaymentPluginApiWithTestControl paymentPluginApi = getTestPluginPaymentApi();
            final RuntimeException e = new RuntimeException("test-error");

            paymentPluginApi.setPaymentRuntimeExceptionOnNextCalls(e);
            paymentPluginApi.processPayment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, Currency.USD, callContext);
            Assert.fail("Expected to fail with " + e.toString());
        } catch (RuntimeException e) {
            gotException = true;
        }
        Assert.assertTrue(gotException);
    }


    @Test(groups = "slow")
    public void testIntegrationOK() throws Exception {
        setupIntegration(null, null);
    }

    @Test(groups = "slow")
    public void testIntegrationWithPaymentPluginApiException() throws Exception {
        final PaymentPluginApiException e = new PaymentPluginApiException("test-error", "foo");
        setupIntegration(e, null);
    }

    @Test(groups = "slow")
    public void testIntegrationWithRuntimeException() throws Exception {
        final RuntimeException e = new RuntimeException("test-error");
        setupIntegration(null, e);
    }


    private void setupIntegration(final PaymentPluginApiException expectedException, final RuntimeException expectedRuntimeException) throws Exception {

        final PaymentPluginApiWithTestControl paymentPluginApi = getTestPluginPaymentApi();

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithOsgiPaymentMethod(accountData);

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        //
        // ADD ADD_ON ON THE SAME DAY TO TRIGGER PAYMENT
        //

        final List<NextEvent> expectedEvents = new LinkedList<NextEvent>();
        expectedEvents.add(NextEvent.CREATE);
        expectedEvents.add(NextEvent.INVOICE);
        if (expectedException == null && expectedRuntimeException == null) {
            expectedEvents.add(NextEvent.PAYMENT);
        } else if (expectedException != null) {
            expectedEvents.add(NextEvent.PAYMENT_PLUGIN_ERROR);
            paymentPluginApi.setPaymentPluginApiExceptionOnNextCalls(expectedException);
        } else if (expectedRuntimeException != null) {
            expectedEvents.add(NextEvent.PAYMENT_PLUGIN_ERROR);
            paymentPluginApi.setPaymentRuntimeExceptionOnNextCalls(expectedRuntimeException);
        }

        final DefaultEntitlement aoEntitlement = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY,
                                                                                       expectedEvents.toArray(new NextEvent[expectedEvents.size()]));


        Invoice invoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("399.95")));

        if (expectedException == null && expectedRuntimeException == null) {
            paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 4, 1), new BigDecimal("399.95"), PaymentStatus.SUCCESS, invoice.getId(), Currency.USD));
        } else if (expectedException != null) {
            paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 4, 1), new BigDecimal("399.95"), PaymentStatus.PLUGIN_FAILURE, invoice.getId(), Currency.USD));
        } else if (expectedRuntimeException != null) {
            paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 4, 1), new BigDecimal("399.95"), PaymentStatus.PLUGIN_FAILURE, invoice.getId(), Currency.USD));
        }
    }


    private PaymentPluginApiWithTestControl getTestPluginPaymentApi() {
        PaymentPluginApiWithTestControl result = (PaymentPluginApiWithTestControl) paymentPluginApiOSGIServiceRegistration.getServiceForName(BeatrixIntegrationModule.OSGI_PLUGIN_NAME);
        Assert.assertNotNull(result);
        return result;
    }
}
