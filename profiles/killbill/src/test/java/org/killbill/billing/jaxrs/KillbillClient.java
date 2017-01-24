/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClient;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.PluginProperty;
import org.killbill.billing.client.model.Subscription;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.tag.ControlTagType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class KillbillClient extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected final int DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC = 10;

    protected static final String PLUGIN_NAME = "noop";

    protected static final String DEFAULT_CURRENCY = "USD";

    // Multi-Tenancy information, if enabled
    protected String DEFAULT_API_KEY = UUID.randomUUID().toString();
    protected String DEFAULT_API_SECRET = UUID.randomUUID().toString();

    // RBAC information, if enabled
    protected String USERNAME = "tester";
    protected String PASSWORD = "tester";

    // Context information to be passed around
    protected static final String createdBy = "Toto";
    protected static final String reason = "i am god";
    protected static final String comment = "no comment";

    protected static RequestOptions requestOptions = RequestOptions.builder()
                                                                   .withCreatedBy(createdBy)
                                                                   .withReason(reason)
                                                                   .withComment(comment)
                                                                   .build();

    protected KillBillClient killBillClient;
    protected KillBillHttpClient killBillHttpClient;

    protected List<PluginProperty> getPaymentMethodCCProperties() {
        final List<PluginProperty> properties = new ArrayList<PluginProperty>();
        properties.add(new PluginProperty("type", "CreditCard", false));
        properties.add(new PluginProperty("cardType", "Visa", false));
        properties.add(new PluginProperty("cardHolderName", "Mr Sniff", false));
        properties.add(new PluginProperty("expirationDate", "2015-08", false));
        properties.add(new PluginProperty("maskNumber", "3451", false));
        properties.add(new PluginProperty("address1", "23, rue des cerisiers", false));
        properties.add(new PluginProperty("address2", "", false));
        properties.add(new PluginProperty("city", "Toulouse", false));
        properties.add(new PluginProperty("country", "France", false));
        properties.add(new PluginProperty("postalCode", "31320", false));
        properties.add(new PluginProperty("state", "Midi-Pyrenees", false));
        return properties;
    }

    protected List<PluginProperty> getPaymentMethodPaypalProperties() {
        final List<PluginProperty> properties = new ArrayList<PluginProperty>();
        properties.add(new PluginProperty("type", "CreditCard", false));
        properties.add(new PluginProperty("email", "zouzou@laposte.fr", false));
        properties.add(new PluginProperty("baid", "23-8787d-R", false));
        return properties;
    }

    protected Account createAccountWithDefaultPaymentMethod(final String externalkey) throws Exception {
        return  createAccountWithDefaultPaymentMethod(externalkey, null);
    }

    protected Account createAccountWithDefaultPaymentMethod() throws Exception {
        return  createAccountWithDefaultPaymentMethod(UUID.randomUUID().toString(), null);
    }

    protected Account createAccountWithDefaultPaymentMethod(final String externalkey, @Nullable final List<PluginProperty> pmProperties) throws Exception {
        final Account input = createAccount();

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(pmProperties);
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, externalkey, input.getAccountId(), true, PLUGIN_NAME, info);
        killBillClient.createPaymentMethod(paymentMethodJson, createdBy, reason, comment);
        return killBillClient.getAccount(input.getExternalKey());
    }

    protected Account createAccountWithExternalPaymentMethod() throws Exception {
        final Account input = createAccount();

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUIDs.randomUUID().toString(), input.getAccountId(),
                                                                  true, ExternalPaymentProviderPlugin.PLUGIN_NAME, info);
        killBillClient.createPaymentMethod(paymentMethodJson, requestOptions);
        return killBillClient.getAccount(input.getExternalKey(), requestOptions);
    }

    protected Account createAccount() throws Exception {
        return createAccount(null);
    }

    protected Account createAccount(final UUID parentAccountId) throws Exception {
        final Account input = getAccount(parentAccountId);
        return killBillClient.createAccount(input, createdBy, reason, comment);
    }

    protected Subscription createEntitlement(final UUID accountId, final String bundleExternalKey, final String productName,
                                             final ProductCategory productCategory, final BillingPeriod billingPeriod, final boolean waitCompletion) throws Exception {
        final Subscription input = new Subscription();
        input.setAccountId(accountId);
        input.setExternalKey(bundleExternalKey);
        input.setProductName(productName);
        input.setProductCategory(productCategory);
        input.setBillingPeriod(billingPeriod);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        return killBillClient.createSubscription(input, null, waitCompletion ? DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC : -1, requestOptions);
    }

    protected Account createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice() throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);
        clock.addDays(32);
        crappyWaitForLackOfProperSynchonization();

        return accountJson;
    }

    protected Account createAccountWithExternalPMBundleAndSubscriptionAndManualPayTagAndWaitForFirstInvoice() throws Exception {
        final Account accountJson = createAccountWithExternalPaymentMethod();
        assertNotNull(accountJson);

        final Tags accountTag = killBillClient.createAccountTag(accountJson.getAccountId(), ControlTagType.MANUAL_PAY.getId(), requestOptions);
        assertNotNull(accountTag);
        assertEquals(accountTag.get(0).getTagDefinitionId(), ControlTagType.MANUAL_PAY.getId());

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);
        clock.addDays(32);
        crappyWaitForLackOfProperSynchonization();

        return accountJson;
    }

    protected Account createAccountNoPMBundleAndSubscription() throws Exception {
        // Create an account with no payment method
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        return accountJson;
    }

    protected Account createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice() throws Exception {
        // Create an account with no payment method
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);
        clock.addMonths(1);
        crappyWaitForLackOfProperSynchonization();

        // No payment will be triggered as the account doesn't have a payment method

        return accountJson;
    }

    protected Account getAccount() {
        return getAccount(null);
    }

    protected Account getAccount(final UUID parentAccountId) {
        return getAccount(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString().substring(0, 5) + '@' + UUID.randomUUID().toString().substring(0, 5), parentAccountId);
    }

    public Account getAccount(final String name, final String externalKey, final String email) {
        return getAccount(name, externalKey, email, null);
    }

    public Account getAccount(final String name, final String externalKey, final String email, final UUID parentAccountId) {
        final UUID accountId = UUID.randomUUID();
        final int length = 4;
        final String currency = DEFAULT_CURRENCY;
        final String timeZone = "UTC";
        final String address1 = "12 rue des ecoles";
        final String address2 = "Poitier";
        final String postalCode = "44 567";
        final String company = "Renault";
        final String city = "Quelque part";
        final String state = "Poitou";
        final String country = "France";
        final String locale = "fr";
        final String phone = "81 53 26 56";
        final String notes = "notes";
        final boolean isPaymentDelegatedToParent = parentAccountId != null;

        // Note: the accountId payload is ignored on account creation
        return new Account(accountId, name, length, externalKey, email, null, currency, parentAccountId, isPaymentDelegatedToParent, null, timeZone,
                           address1, address2, postalCode, company, city, state, country, locale, phone, notes, false, false, null, null);
    }

    /**
     * We could implement a ClockResource in jaxrs with the ability to sync on user token
     * but until we have a strong need for it, this is in the TODO list...
     */
    protected void crappyWaitForLackOfProperSynchonization() throws Exception {
        crappyWaitForLackOfProperSynchonization(5000);
    }


    protected void crappyWaitForLackOfProperSynchonization(int sleepValueMSec) throws Exception {
        Thread.sleep(sleepValueMSec);
    }

}
