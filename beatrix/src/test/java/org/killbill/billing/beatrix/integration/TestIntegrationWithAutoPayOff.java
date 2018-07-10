/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collection;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.config.definition.PaymentConfig;

import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestIntegrationWithAutoPayOff extends TestIntegrationBase {

    private Account account;
    private SubscriptionBaseBundle bundle;
    private String productName;
    private BillingPeriod term;
    private String planSetName;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
    }

    @Test(groups = "slow")
    public void testAutoPayOff() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(40); // After trial

        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        addDelayBceauseOfLackOfCorrectSynchro();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertTrue(cur.getBalance().compareTo(BigDecimal.ZERO) == 0);
            assertTrue(cur.getPaidAmount().compareTo(cur.getChargedAmount()) == 0);
        }
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testAutoPayOffWithPaymentFailure() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(40); // After trial

        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        paymentPlugin.makeNextPaymentFailWithError();
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        addDelayBceauseOfLackOfCorrectSynchro();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }
        assertListenerStatus();

        int nbDaysBeforeRetry = paymentConfig.getPaymentFailureRetryDays(internalCallContext).get(0);

        // MOVE TIME FOR RETRY TO HAPPEN
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(nbDaysBeforeRetry + 1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertTrue(cur.getBalance().compareTo(BigDecimal.ZERO) == 0);
            assertTrue(cur.getPaidAmount().compareTo(cur.getChargedAmount()) == 0);
        }
        assertListenerStatus();

    }

    @Test(groups = "slow")
    public void testAutoPayOffWithPaymentFailureOn_AUTO_PAY_OFF() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);

        // CREATE FIRST NON NULL INVOICE + FIRST PAYMENT/ATTEMPT -> AUTO_PAY_OFF
        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(31); // After trial
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        // NOW SET PLUGIN TO THROW FAILURES
        paymentPlugin.makeNextPaymentFailWithError();
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        addDelayBceauseOfLackOfCorrectSynchro();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }
        assertListenerStatus();

        // RE-ADD AUTO_PAY_OFF to ON
        int nbDaysBeforeRetry = paymentConfig.getPaymentFailureRetryDays(internalCallContext).get(0);
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // MOVE TIME FOR RETRY TO HAPPEN -> WILL BE DISCARDED SINCE AUTO_PAY_OFF IS SET
        clock.addDays(nbDaysBeforeRetry + 1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }
        // We want to give some time for the retry to fail before we start clearing the state
        addDelayBceauseOfLackOfCorrectSynchro();

        // REMOVE AUTO_PAY_OFF -> WILL SCHEDULE A PAYMENT_RETRY
        paymentPlugin.clear();
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        addDelayBceauseOfLackOfCorrectSynchro();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertTrue(cur.getBalance().compareTo(BigDecimal.ZERO) == 0);
            assertTrue(cur.getPaidAmount().compareTo(cur.getChargedAmount()) == 0);
        }
        assertListenerStatus();

    }


    private void addDelayBceauseOfLackOfCorrectSynchro() {
        // TODO When removing the tag, the payment system will schedule retries for payments that are in non terminal state
        // The issue is that at this point we know the event went on the bus but we don't know if the listener in payment completed
        // so we add some delay to ensure that it had time to complete. Failure to do so introduces some flakiness in the test because the clock
        // is moved right after that, and so payment may see the new value.
        //
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignore) {
        }
    }
}

