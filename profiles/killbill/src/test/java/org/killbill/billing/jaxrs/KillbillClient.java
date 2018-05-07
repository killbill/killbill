/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.api.gen.AccountApi;
import org.killbill.billing.client.api.gen.AdminApi;
import org.killbill.billing.client.api.gen.BundleApi;
import org.killbill.billing.client.api.gen.CatalogApi;
import org.killbill.billing.client.api.gen.CreditApi;
import org.killbill.billing.client.api.gen.CustomFieldApi;
import org.killbill.billing.client.api.gen.ExportApi;
import org.killbill.billing.client.api.gen.InvoiceApi;
import org.killbill.billing.client.api.gen.InvoiceItemApi;
import org.killbill.billing.client.api.gen.InvoicePaymentApi;
import org.killbill.billing.client.api.gen.NodesInfoApi;
import org.killbill.billing.client.api.gen.OverdueApi;
import org.killbill.billing.client.api.gen.PaymentApi;
import org.killbill.billing.client.api.gen.PaymentGatewayApi;
import org.killbill.billing.client.api.gen.PaymentMethodApi;
import org.killbill.billing.client.api.gen.PaymentTransactionApi;
import org.killbill.billing.client.api.gen.PluginInfoApi;
import org.killbill.billing.client.api.gen.SecurityApi;
import org.killbill.billing.client.api.gen.SubscriptionApi;
import org.killbill.billing.client.api.gen.TagApi;
import org.killbill.billing.client.api.gen.TagDefinitionApi;
import org.killbill.billing.client.api.gen.TenantApi;
import org.killbill.billing.client.api.gen.UsageApi;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.gen.PluginProperty;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.tag.ControlTagType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class KillbillClient extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final ImmutableList<String> NULL_PLUGIN_NAMES = null;
    protected static final ImmutableMap<String, String> NULL_PLUGIN_PROPERTIES = null;
    protected static final ImmutableList<AuditLog> EMPTY_AUDIT_LOGS = ImmutableList.<AuditLog>of();



    protected final long DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC = 10;

    protected static final String PLUGIN_NAME = "noop";

    protected static final Currency DEFAULT_CURRENCY = Currency.USD;

    // static to be shared across test class instances (initialized once in @BeforeSuite)
    protected static CallbackServlet callbackServlet;

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

    protected KillBillHttpClient killBillHttpClient;

    protected AccountApi accountApi;
    protected AdminApi adminApi;
    protected BundleApi bundleApi;
    protected CatalogApi catalogApi;
    protected CreditApi creditApi;
    protected CustomFieldApi customFieldApi;
    protected ExportApi exportApi;
    protected InvoiceApi invoiceApi;
    protected InvoiceItemApi invoiceItemApi;
    protected InvoicePaymentApi invoicePaymentApi;
    protected NodesInfoApi nodesInfoApi;
    protected OverdueApi overdueApi;
    protected PaymentApi paymentApi;
    protected PaymentGatewayApi paymentGatewayApi;
    protected PaymentMethodApi paymentMethodApi;
    protected PaymentTransactionApi paymentTransactionApi;
    protected PluginInfoApi pluginInfoApi;
    protected SecurityApi securityApi;
    protected SubscriptionApi subscriptionApi;
    protected TagApi tagApi;
    protected TagDefinitionApi tagDefinitionApi;
    protected TenantApi tenantApi;
    protected UsageApi usageApi;

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
        return createAccountWithDefaultPaymentMethod(externalkey, null);
    }

    protected Account createAccountWithDefaultPaymentMethod() throws Exception {
        return createAccountWithDefaultPaymentMethod(UUID.randomUUID().toString(), null);
    }

    protected Account createAccountWithDefaultPaymentMethod(final String externalkey, @Nullable final List<PluginProperty> pmProperties) throws Exception {
        final Account input = createAccount();

        callbackServlet.pushExpectedEvent(ExtBusEventType.ACCOUNT_CHANGE);
        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(pmProperties);
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, externalkey, input.getAccountId(), true, PLUGIN_NAME, info, EMPTY_AUDIT_LOGS);
        accountApi.createPaymentMethod(input.getAccountId(), paymentMethodJson, true, false, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        return accountApi.getAccount(input.getAccountId(), requestOptions);
    }

    protected Account createAccountWithExternalPaymentMethod() throws Exception {
        final Account input = createAccount();
        createPaymentMethod(input, true);
        return accountApi.getAccount(input.getAccountId(), requestOptions);
    }

    protected PaymentMethod createPaymentMethod(final Account input, final boolean isDefault) throws KillBillClientException {
        if (isDefault) {
            callbackServlet.pushExpectedEvent(ExtBusEventType.ACCOUNT_CHANGE);
        }
        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUIDs.randomUUID().toString(), input.getAccountId(),
                                                                  isDefault, ExternalPaymentProviderPlugin.PLUGIN_NAME, info, EMPTY_AUDIT_LOGS);
        final PaymentMethod paymentMethod = accountApi.createPaymentMethod(input.getAccountId(), paymentMethodJson, isDefault, false, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        return paymentMethod;
    }

    protected Account createAccount() throws Exception {
        return createAccount(null);
    }

    protected Account createAccount(final UUID parentAccountId) throws Exception {
        callbackServlet.pushExpectedEvent(ExtBusEventType.ACCOUNT_CREATION);
        final Account account = createAccountNoEvent(parentAccountId);
        callbackServlet.assertListenerStatus();
        return account;
    }

    protected Account createAccountNoEvent(final UUID parentAccountId) throws KillBillClientException {
        final Account input = getAccount(parentAccountId);
        return accountApi.createAccount(input, requestOptions);
    }

    protected Subscription createSubscription(final UUID accountId, final String bundleExternalKey, final String productName,
                                              final ProductCategory productCategory, final BillingPeriod billingPeriod, final boolean waitCompletion) throws Exception {
        final Account account = accountApi.getAccount(accountId, requestOptions);
        if (account.getBillCycleDayLocal() == null || account.getBillCycleDayLocal() == 0) {
            callbackServlet.pushExpectedEvent(ExtBusEventType.ACCOUNT_CHANGE);
        }
        callbackServlet.pushExpectedEvents(ExtBusEventType.ENTITLEMENT_CREATION, ExtBusEventType.SUBSCRIPTION_CREATION, ExtBusEventType.SUBSCRIPTION_CREATION, ExtBusEventType.INVOICE_CREATION);

        final Subscription input = new Subscription();
        input.setAccountId(accountId);
        input.setExternalKey(bundleExternalKey);
        input.setProductName(productName);
        input.setProductCategory(productCategory);
        input.setBillingPeriod(billingPeriod);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription subscription = subscriptionApi.createSubscription(input, null, null, true, false, null, waitCompletion, waitCompletion ? DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC : -1L, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();

        return subscription;
    }

    protected Account createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice() throws Exception {
        return createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(true);
    }

    protected Account createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(final boolean paymentSuccess) throws Exception {
        return createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice("Shotgun", paymentSuccess);
    }

    protected Account createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(final String productName, final boolean paymentSuccess) throws Exception {
        return createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(productName, paymentSuccess, paymentSuccess);
    }

    protected Account createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(final String productName, final boolean invoicePaymentSuccess, final boolean paymentSuccess) throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createSubscription(accountJson.getAccountId(), UUID.randomUUID().toString(), productName,
                                                                 ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE, ExtBusEventType.INVOICE_CREATION);
        if (invoicePaymentSuccess) {
            callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_PAYMENT_SUCCESS);
        } else {
            callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_PAYMENT_FAILED);
        }
        if (paymentSuccess) {
            callbackServlet.pushExpectedEvents(ExtBusEventType.PAYMENT_SUCCESS);
        } else {
            callbackServlet.pushExpectedEvents(ExtBusEventType.PAYMENT_FAILED);
        }
        clock.addDays(32);
        callbackServlet.assertListenerStatus();

        return accountJson;
    }

    protected Account createAccountWithExternalPMBundleAndSubscriptionAndManualPayTagAndWaitForFirstInvoice() throws Exception {
        final Account accountJson = createAccountWithExternalPaymentMethod();
        assertNotNull(accountJson);

        callbackServlet.pushExpectedEvent(ExtBusEventType.TAG_CREATION);
        final Tags accountTag = accountApi.createAccountTags(accountJson.getAccountId(), ImmutableList.<UUID>of(ControlTagType.MANUAL_PAY.getId()), requestOptions);
        callbackServlet.assertListenerStatus();
        assertNotNull(accountTag);
        assertEquals(accountTag.get(0).getTagDefinitionId(), ControlTagType.MANUAL_PAY.getId());

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createSubscription(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE, ExtBusEventType.INVOICE_CREATION);
        clock.addDays(32);
        callbackServlet.assertListenerStatus();

        return accountJson;
    }

    protected Account createAccountNoPMBundleAndSubscription() throws Exception {
        // Create an account with no payment method
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createSubscription(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        return accountJson;
    }

    protected Account createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice() throws Exception {
        // Create an account with no payment method
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // Add a bundle, subscription and move the clock to get the first invoice
        final Subscription subscriptionJson = createSubscription(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        // No payment will be triggered as the account doesn't have a payment method
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE, ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_FAILED);
        clock.addMonths(1);
        callbackServlet.assertListenerStatus();

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
        final Currency currency = DEFAULT_CURRENCY;
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
        return new Account(accountId, name, length, externalKey, email, null, currency, parentAccountId, isPaymentDelegatedToParent, null, null, timeZone,
                           address1, address2, postalCode, company, city, state, country, locale, phone, notes, false, false, null, null, EMPTY_AUDIT_LOGS);
    }
}
