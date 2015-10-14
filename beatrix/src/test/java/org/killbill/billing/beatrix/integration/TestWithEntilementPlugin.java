/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApi;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApiException;
import org.killbill.billing.entitlement.plugin.api.OnFailureEntitlementResult;
import org.killbill.billing.entitlement.plugin.api.OnSuccessEntitlementResult;
import org.killbill.billing.entitlement.plugin.api.PriorEntitlementResult;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestWithEntilementPlugin extends TestIntegrationBase {

    @Inject
    private OSGIServiceRegistration<EntitlementPluginApi> pluginRegistry;

    @Inject
    private InternalCallContextFactory internalCallContextFactory;

    private TestEntitlementPluginApi testEntitlementPluginApi;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();

        this.testEntitlementPluginApi = new TestEntitlementPluginApi();
        pluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return "TestEntitlementPluginApi";
            }

            @Override
            public String getPluginName() {
                return "TestEntitlementPluginApi";
            }

            @Override
            public String getRegistrationName() {
                return "TestEntitlementPluginApi";
            }
        }, testEntitlementPluginApi);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        testEntitlementPluginApi.setPlanPhasePriceOverride(null);
    }


        @Test(groups = "slow")
    public void testCreateSubscriptionWithEntitlementPlugin() throws Exception {

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride("shotgun-monthly-evergreen", account.getCurrency(), null, BigDecimal.TEN));

        testEntitlementPluginApi.setPlanPhasePriceOverride(overrides);

        //
        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);


        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, BigDecimal.TEN));

        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, BigDecimal.TEN));
    }

    public static class TestEntitlementPluginApi implements EntitlementPluginApi {

        private List<PlanPhasePriceOverride> planPhasePriceOverride;

        public TestEntitlementPluginApi() {
        }

        @Override
        public PriorEntitlementResult priorCall(final EntitlementContext entitlementContext, final Iterable<PluginProperty> properties) throws EntitlementPluginApiException {
            if (planPhasePriceOverride != null) {
                return new PriorEntitlementResult() {
                    @Override
                    public boolean isAborted() {
                        return false;
                    }

                    @Override
                    public PlanPhaseSpecifier getAdjustedPlanPhaseSpecifier() {
                        return null;
                    }

                    @Override
                    public LocalDate getAdjustedEffectiveDate() {
                        return null;
                    }

                    @Override
                    public List<PlanPhasePriceOverride> getAdjustedPlanPhasePriceOverride() {
                        return planPhasePriceOverride;
                    }

                    @Override
                    public Iterable<PluginProperty> getAdjustedPluginProperties() {
                        return null;
                    }
                };
            }
            return null;
        }

        @Override
        public OnSuccessEntitlementResult onSuccessCall(final EntitlementContext entitlementContext, final Iterable<PluginProperty> properties) throws EntitlementPluginApiException {
            return null;
        }

        @Override
        public OnFailureEntitlementResult onFailureCall(final EntitlementContext entitlementContext, final Iterable<PluginProperty> properties) throws EntitlementPluginApiException {
            return null;
        }

        public void setPlanPhasePriceOverride(final List<PlanPhasePriceOverride> planPhasePriceOverride) {
            this.planPhasePriceOverride = planPhasePriceOverride;
        }
    }

}
