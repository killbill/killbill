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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.user.DefaultSimplePlanDescriptor;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.tenant.api.DefaultTenant;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationWithCatalogUpdate extends TestIntegrationBase {

    @Inject
    private CatalogUserApi catalogUserApi;

    private Tenant tenant;
    private CallContext testCallContext;
    private Account account;

    private DateTime init;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();

        // Set original time
        clock.setDay(new LocalDate(2016, 6, 1));
        init = clock.getUTCNow();

        // Setup tenant
        setupTenant();

        // Setup account in right tenant
        setupAccount();
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor desc1 = new DefaultSimplePlanDescriptor("foo-monthly", "Foo", account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED);
        catalogUserApi.addSimplePlan(desc1, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().length, 1);

        final Entitlement baseEntitlement = createEntitlement("Foo", BillingPeriod.MONTHLY);

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.RECURRING, BigDecimal.TEN));

        // Add another Plan in the catalog
        final SimplePlanDescriptor desc2 = new DefaultSimplePlanDescriptor("superfoo-monthly", "SuperFoo", account.getCurrency(), new BigDecimal("20.00"), BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED);
        catalogUserApi.addSimplePlan(desc2, init, testCallContext);
        catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().length, 2);


        // Change Plan to the newly added Plan and verify correct default rules behavior (IMMEDIATE change)
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        baseEntitlement.changePlan("SuperFoo", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("20.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-10.00")));
        assertListenerStatus();
    }


    private Entitlement createEntitlement(String productName, BillingPeriod billingPeriod) throws EntitlementApiException {
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, UUID.randomUUID().toString(), null, null, null, false, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();
        return entitlement;
    }


    private void setupTenant() throws TenantApiException {
        final UUID uuid = UUID.randomUUID();
        final String externalKey = uuid.toString();
        final String apiKey = externalKey + "-Key";
        final String apiSecret = externalKey + "-$3cr3t";
        // Only place where we use callContext
        tenant = tenantUserApi.createTenant(new DefaultTenant(uuid, init, init, externalKey, apiKey, apiSecret), callContext);

        testCallContext = new DefaultCallContext(tenant.getId(), "tester", CallOrigin.EXTERNAL, UserType.TEST,
                                      "good reason", "trust me", uuid, clock);
    }

    private void setupAccount() throws Exception {

        final AccountData accountData = getAccountData(1);
        account = accountUserApi.createAccount(accountData, testCallContext);
        assertNotNull(account);

        final PaymentMethodPlugin info = createPaymentMethodPlugin();
        paymentApi.addPaymentMethod(account, UUID.randomUUID().toString(),  BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME, true, info, PLUGIN_PROPERTIES, testCallContext);
    }

}
