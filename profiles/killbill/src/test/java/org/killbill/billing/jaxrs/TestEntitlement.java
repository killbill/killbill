/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Bundle;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.PhasePriceOverride;
import org.killbill.billing.client.model.Subscription;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestEntitlement extends TestJaxrsBase {

    private static final int CALL_COMPLETION_TIMEOUT_SEC = 5;

    @Test(groups = "slow", description = "Can change plan and cancel a subscription")
    public void testEntitlementInTrialOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                               ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        Assert.assertEquals(objFromJson.getPriceOverrides().size(), 2);
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPriceOverrides().get(0).getRecurringPrice());

        Assert.assertNull(objFromJson.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        entitlementJson.setPriceOverrides(null);
        Assert.assertTrue(objFromJson.equals(entitlementJson));

        // Change plan IMM
        final String newProductName = "Assault-Rifle";

        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setProductName(newProductName);
        newInput.setProductCategory(ProductCategory.BASE);
        newInput.setBillingPeriod(entitlementJson.getBillingPeriod());
        newInput.setPriceList(entitlementJson.getPriceList());
        objFromJson = killBillClient.updateSubscription(newInput, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);
        Assert.assertNotNull(objFromJson);

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        // Cancel IMM (Billing EOT)
        killBillClient.cancelSubscription(newInput.getSubscriptionId(), CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);

        // Retrieves to check EndDate
        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        assertNotNull(objFromJson.getCancelledDate());
        assertTrue(objFromJson.getCancelledDate().compareTo(new LocalDate(clock.getUTCNow())) == 0);
    }

    @Test(groups = "slow", description = "Can cancel and uncancel a subscription")
    public void testEntitlementUncancel() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                               ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        entitlementJson.setPriceOverrides(null);

        Assert.assertTrue(objFromJson.equals(entitlementJson));

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        // Cancel EOT
        killBillClient.cancelSubscription(entitlementJson.getSubscriptionId(), EntitlementActionPolicy.END_OF_TERM,
                                          BillingActionPolicy.END_OF_TERM, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);

        // Retrieves to check EndDate
        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        assertNotNull(objFromJson.getCancelledDate());

        // Uncancel
        killBillClient.uncancelSubscription(entitlementJson.getSubscriptionId(), createdBy, reason, comment);

        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        assertNull(objFromJson.getCancelledDate());
    }

    @Test(groups = "slow", description = "Can handle non existent subscription")
    public void testWithNonExistentEntitlement() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final Subscription subscription = new Subscription();
        subscription.setAccountId(UUID.randomUUID());
        subscription.setSubscriptionId(subscriptionId);
        subscription.setProductName("Pistol");
        subscription.setProductCategory(ProductCategory.BASE);
        subscription.setBillingPeriod(BillingPeriod.ANNUAL);
        subscription.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        assertNull(killBillClient.updateSubscription(subscription, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment));

        killBillClient.cancelSubscription(subscriptionId, createdBy, reason, comment);

        assertNull(killBillClient.getSubscription(subscriptionId));
    }

    @Test(groups = "slow", description = "Can override billing policy on change")
    public void testOverridePolicy() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = killBillClient.getSubscription(subscriptionJson.getSubscriptionId());
        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        subscriptionJson.setPriceOverrides(null);

        Assert.assertTrue(objFromJson.equals(subscriptionJson));
        assertEquals(objFromJson.getBillingPeriod(), BillingPeriod.ANNUAL);

        // Change billing period immediately
        final Subscription newInput = new Subscription();
        newInput.setAccountId(accountJson.getAccountId());
        newInput.setSubscriptionId(subscriptionJson.getSubscriptionId());
        newInput.setProductName(subscriptionJson.getProductName());
        newInput.setProductCategory(ProductCategory.BASE);
        newInput.setBillingPeriod(BillingPeriod.MONTHLY);
        newInput.setPriceList(subscriptionJson.getPriceList());
        objFromJson = killBillClient.updateSubscription(newInput, BillingActionPolicy.IMMEDIATE, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);
        Assert.assertNotNull(objFromJson);
        assertEquals(objFromJson.getBillingPeriod(), BillingPeriod.MONTHLY);
    }

    @Test(groups = "slow", description = "Can override a price when creating a subscription")
    public void testOverridePrice() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setExternalKey("identical");
        input.setProductName(productName);
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final List<PhasePriceOverride> overrides = new ArrayList<PhasePriceOverride>();
        overrides.add(new PhasePriceOverride(null, PhaseType.TRIAL.toString(), BigDecimal.TEN, null));
        input.setPriceOverrides(overrides);

        final Subscription subscription = killBillClient.createSubscription(input, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, basicRequestOptions());

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, AuditLevel.FULL);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getAmount().compareTo(BigDecimal.TEN), 0);
    }

    @Test(groups = "slow", description = "Create a base entitlement and also addOns entitlements under the same bundle")
    public void testEntitlementWithAddOns() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription base = new Subscription();
        base.setAccountId(accountJson.getAccountId());
        base.setExternalKey("base");
        base.setProductName("Shotgun");
        base.setProductCategory(ProductCategory.BASE);
        base.setBillingPeriod(BillingPeriod.MONTHLY);
        base.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setExternalKey("");
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn2 = new Subscription();
        addOn2.setAccountId(accountJson.getAccountId());
        addOn2.setExternalKey("");
        addOn2.setProductName("Laser-Scope");
        addOn2.setProductCategory(ProductCategory.ADD_ON);
        addOn2.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn2.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions = new ArrayList<Subscription>();
        subscriptions.add(base);
        subscriptions.add(addOn1);
        subscriptions.add(addOn2);
        final Bundle bundle = killBillClient.createSubscriptionWithAddOns(subscriptions, initialDate.toLocalDate(), 10, "createdBy", "", "");
        assertNotNull(bundle);
        assertEquals(bundle.getExternalKey(), "base");
        assertEquals(bundle.getSubscriptions().size(), 3);

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, AuditLevel.FULL);
        assertEquals(invoices.size(), 1);
    }

    @Test(groups = "slow", description = "Can create an entitlement in the future")
    public void testCreateEntitlementInTheFuture() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = killBillClient.createSubscription(input, initialDate.toLocalDate().plusMonths(1), -1, createdBy, reason, comment);

        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());
        Assert.assertEquals(entitlementJson.getProductCategory(), input.getProductCategory());
        Assert.assertEquals(entitlementJson.getBillingPeriod(), input.getBillingPeriod());
        Assert.assertEquals(entitlementJson.getPriceList(), input.getPriceList());

        // Retrieves with GET
        final Subscription objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        Assert.assertTrue(objFromJson.equals(entitlementJson));
    }

    @Test(groups = "slow", description = "Can create an entitlement with an account with autoPayOff")
    public void testCreateEntitlementWithAutoPayOff() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // assign autoPaymentOff tag to account
        Tags tags = killBillClient.createAccountTag(accountJson.getAccountId(), new UUID(0L, 1L), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");

        // verify that number of invoices and payments for account is still 0
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions).size(), 0);
        assertEquals(killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions).size(), 0);

        // create a subscription with no trial plan
        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Blowdart");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");
        final Subscription subscriptionJson = killBillClient.createSubscription(input, null, 10, requestOptions);
        assertNotNull(subscriptionJson);

        // verify that number of invoices is now 1
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions).size(), 1);

        // verify that number of payments is still 0 (no attempts)
        assertEquals(killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions).size(), 0);
    }

    @Test(groups = "slow", description = "Verify we can move the BCD associated with the subscription")
    public void testMoveEntitlementBCD() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                               ProductCategory.BASE, term, true);

        Assert.assertEquals(entitlementJson.getBillCycleDayLocal(), new Integer(25));

        final Subscription updatedSubscription = new Subscription();
        updatedSubscription.setSubscriptionId(entitlementJson.getSubscriptionId());
        updatedSubscription.setBillCycleDayLocal(9);
        killBillClient.updateSubscriptionBCD(updatedSubscription, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, basicRequestOptions());


        final Subscription result = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        // Still shows as the 4 (BCD did not take effect)
        Assert.assertEquals(result.getBillCycleDayLocal(), new Integer(25));

        // 2012, 5, 9
        clock.addDays(14);
        crappyWaitForLackOfProperSynchonization();

        final Subscription result2 = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        // Still shows as the 4 (BCD did not take effect)
        Assert.assertEquals(result2.getBillCycleDayLocal(), new Integer(9));
    }



    @Test(groups = "slow", description = "Can create subscription and change plan using planName")
    public void testEntitlementUsingPlanName() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setExternalKey("somethingSpecial");
        input.setPlanName("shotgun-monthly");

        final Subscription entitlementJson = killBillClient.createSubscription(input, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, basicRequestOptions());
        Assert.assertEquals(entitlementJson.getProductName(), "Shotgun");
        Assert.assertEquals(entitlementJson.getBillingPeriod(), BillingPeriod.MONTHLY);
        Assert.assertEquals(entitlementJson.getPriceList(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(entitlementJson.getPlanName(), "shotgun-monthly");


        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        final Subscription newEntitlementJson = killBillClient.updateSubscription(newInput, null, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, basicRequestOptions());
        Assert.assertEquals(newEntitlementJson.getProductName(), "Pistol");
        Assert.assertEquals(newEntitlementJson.getBillingPeriod(), BillingPeriod.MONTHLY);
        Assert.assertEquals(newEntitlementJson.getPriceList(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(newEntitlementJson.getPlanName(), "pistol-monthly");

    }


}
