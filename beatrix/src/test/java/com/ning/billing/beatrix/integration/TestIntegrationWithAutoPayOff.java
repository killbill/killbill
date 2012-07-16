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
package com.ning.billing.beatrix.integration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import junit.framework.Assert;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

@Guice(modules = {BeatrixModule.class})
public class TestIntegrationWithAutoPayOff extends TestIntegrationBase {


    @Inject
    private InvoiceUserApi invoiceApi;

    @Inject
    private TagUserApi tagApi;

    @Inject
    private PaymentConfig paymentConfig;


    private Account account;
    private SubscriptionBundle bundle;
    private String productName;
    private BillingPeriod term;
    private String planSetName;


    @BeforeMethod(groups = {"slow"})
    public void setupBeforeTest() throws Exception {

        account = createAccountWithPaymentMethod(getAccountData(25));
        assertNotNull(account);

        bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
    }

    @Test(groups = {"slow"}, enabled = true)
    public void testAutoPayOff() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        busHandler.pushExpectedEvents(NextEvent.CREATE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId());
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(40); // After trial

        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId());
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        busHandler.pushExpectedEvents(NextEvent.PAYMENT);
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId());
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


    @Test(groups = {"slow"}, enabled = true)
    public void testAutoPayOffWithPaymentFailure() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        busHandler.pushExpectedEvents(NextEvent.CREATE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId());
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(40); // After trial

        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId());
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        paymentPlugin.makeNextPaymentFailWithError();
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR);
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId());
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
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId());
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertTrue(cur.getBalance().compareTo(BigDecimal.ZERO) == 0);
            assertTrue(cur.getPaidAmount().compareTo(cur.getChargedAmount()) == 0);
        }
        assertListenerStatus();

    }


    @Test(groups = {"slow"}, enabled = true)
    public void testAutoPayOffWithPaymentFailureOn_AUTO_PAY_OFF() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        busHandler.pushExpectedEvents(NextEvent.CREATE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId());
        assertEquals(invoices.size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(40); // After trial

        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId());
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }

        paymentPlugin.makeNextPaymentFailWithError();
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR);
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId());
        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }
        assertListenerStatus();

        int nbDaysBeforeRetry = paymentConfig.getPaymentRetryDays().get(0);

        // AUTO_PAY_OFF to ON
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // MOVE TIME FOR RETRY TO HAPPEN
        clock.addDays(nbDaysBeforeRetry + 1);
        assertTrue(busHandler.isCompleted(DELAY));

        assertEquals(invoices.size(), 2);
        for (Invoice cur : invoices) {
            if (cur.getChargedAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            assertEquals(cur.getBalance(), cur.getChargedAmount());
        }
        assertListenerStatus();

        // SWICTH BACK AUTO_PAY_OFF OFF
        busHandler.pushExpectedEvents(NextEvent.PAYMENT);
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertTrue(busHandler.isCompleted(DELAY));


        invoices = invoiceApi.getInvoicesByAccount(account.getId());
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
        final TagDefinition def = tagApi.getTagDefinition(ControlTagType.AUTO_PAY_OFF.name());
        tagApi.addTag(id, type, def, context);
        final Map<String, Tag> tags = tagApi.getTags(id, type);
        assertNotNull(tags.get(ControlTagType.AUTO_PAY_OFF.name()));
    }

    private void remove_AUTO_PAY_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        final TagDefinition def = tagApi.getTagDefinition(ControlTagType.AUTO_PAY_OFF.name());
        tagApi.removeTag(id, type, def, context);
    }
}

