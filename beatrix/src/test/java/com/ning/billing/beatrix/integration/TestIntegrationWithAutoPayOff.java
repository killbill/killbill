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

package com.ning.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.DefaultEntitlement;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestIntegrationWithAutoPayOff extends TestIntegrationBase {

    @Inject
    private InvoiceUserApi invoiceApi;

    @Inject
    private TagUserApi tagApi;

    @Inject
    private PaymentConfig paymentConfig;

    private Account account;
    private SubscriptionBaseBundle bundle;
    private String productName;
    private BillingPeriod term;
    private String planSetName;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
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

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(40); // After trial

        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        busHandler.pushExpectedEvents(NextEvent.PAYMENT);
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();
        addDelayBceauseOfLackOfCorrectSynchro();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
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

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(40); // After trial

        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        paymentPlugin.makeNextPaymentFailWithError();
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR);
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();
        addDelayBceauseOfLackOfCorrectSynchro();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }
        assertListenerStatus();

        int nbDaysBeforeRetry = paymentConfig.getPaymentRetryDays().get(0);

        // MOVE TIME FOR RETRY TO HAPPEN
        busHandler.pushExpectedEvents(NextEvent.PAYMENT);
        clock.addDays(nbDaysBeforeRetry + 1);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
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

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1);

        // CREATE FIRST NON NULL INVOICE + FIRST PAYMENT/ATTEMPT -> AUTO_PAY_OFF
        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(31); // After trial
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        // NOW SET PLUGIN TO THROW FAILURES
        paymentPlugin.makeNextPaymentFailWithError();
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR);
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();
        addDelayBceauseOfLackOfCorrectSynchro();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }
        assertListenerStatus();

        // RE-ADD AUTO_PAY_OFF to ON
        int nbDaysBeforeRetry = paymentConfig.getPaymentRetryDays().get(0);
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // MOVE TIME FOR RETRY TO HAPPEN -> WILL BE DISCARDED SINCE AUTO_PAY_OFF IS SET
        clock.addDays(nbDaysBeforeRetry + 1);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
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
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertListenerStatus();
        addDelayBceauseOfLackOfCorrectSynchro();

        //
        busHandler.pushExpectedEvents(NextEvent.PAYMENT);
        clock.addDays(nbDaysBeforeRetry + 1);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertTrue(cur.getBalance().compareTo(BigDecimal.ZERO) == 0);
            assertTrue(cur.getPaidAmount().compareTo(cur.getChargedAmount()) == 0);
        }
        assertListenerStatus();

    }

    private void add_AUTO_PAY_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        busHandler.pushExpectedEvent(NextEvent.TAG);
        tagApi.addTag(id, type, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
        assertListenerStatus();

        final List<Tag> tags = tagApi.getTagsForObject(id, type, false, callContext);
        assertEquals(tags.size(), 1);
    }

    private void remove_AUTO_PAY_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        busHandler.pushExpectedEvent(NextEvent.TAG);
        tagApi.removeTag(id, type, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
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

