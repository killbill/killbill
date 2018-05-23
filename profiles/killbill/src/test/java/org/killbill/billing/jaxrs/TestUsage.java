/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Subscriptions;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Bundle;
import org.killbill.billing.client.model.gen.InvoiceItem;
import org.killbill.billing.client.model.gen.RolledUpUsage;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.client.model.gen.SubscriptionUsageRecord;
import org.killbill.billing.client.model.gen.UnitUsageRecord;
import org.killbill.billing.client.model.gen.UsageRecord;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class TestUsage extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can record and retrieve usage data")
    public void testRecordUsage() throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription base = new Subscription();
        base.setAccountId(accountJson.getAccountId());
        base.setProductName("Pistol");
        base.setProductCategory(ProductCategory.BASE);
        base.setBillingPeriod(BillingPeriod.MONTHLY);
        base.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn = new Subscription();
        addOn.setAccountId(accountJson.getAccountId());
        addOn.setProductName("Bullets");
        addOn.setProductCategory(ProductCategory.ADD_ON);
        addOn.setBillingPeriod(BillingPeriod.NO_BILLING_PERIOD);
        addOn.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.INVOICE_CREATION);
        final Subscriptions body = new Subscriptions();
        body.add(base);
        body.add(addOn);

        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(body,
                                                                           null,
                                                                           null,
                                                                           NULL_PLUGIN_PROPERTIES,
                                                                           requestOptions);
        callbackServlet.assertListenerStatus();
        final UUID addOnSubscriptionId = Iterables.<Subscription>find(bundle.getSubscriptions(),
                                                                      new Predicate<Subscription>() {
                                                                          @Override
                                                                          public boolean apply(final Subscription input) {
                                                                              return ProductCategory.ADD_ON.equals(input.getProductCategory());
                                                                          }
                                                                      }).getSubscriptionId();

        clock.addDays(1);

        final UsageRecord usageRecord1 = new UsageRecord();
        usageRecord1.setAmount(10L);
        usageRecord1.setRecordDate(clock.getUTCToday().minusDays(1));

        final UsageRecord usageRecord2 = new UsageRecord();
        usageRecord2.setAmount(5L);
        usageRecord2.setRecordDate(clock.getUTCToday());

        final UnitUsageRecord unitUsageRecord = new UnitUsageRecord();
        unitUsageRecord.setUnitType("bullets");
        unitUsageRecord.setUsageRecords(ImmutableList.<UsageRecord>of(usageRecord1, usageRecord2));

        final SubscriptionUsageRecord usage = new SubscriptionUsageRecord();
        usage.setSubscriptionId(addOnSubscriptionId);
        usage.setUnitUsageRecords(ImmutableList.<UnitUsageRecord>of(unitUsageRecord));

        usageApi.recordUsage(usage, requestOptions);
        callbackServlet.assertListenerStatus();

        final RolledUpUsage retrievedUsage1 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday(), requestOptions);
        Assert.assertEquals(retrievedUsage1.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage1.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage1.getRolledUpUnits().get(0).getUnitType(), unitUsageRecord.getUnitType());
        // endDate is excluded
        Assert.assertEquals((long) retrievedUsage1.getRolledUpUnits().get(0).getAmount(), 10);

        final RolledUpUsage retrievedUsage2 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday().plusDays(1), requestOptions);
        Assert.assertEquals(retrievedUsage2.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage2.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage2.getRolledUpUnits().get(0).getUnitType(), unitUsageRecord.getUnitType());
        Assert.assertEquals((long) retrievedUsage2.getRolledUpUnits().get(0).getAmount(), 15);

        final RolledUpUsage retrievedUsage3 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday(), clock.getUTCToday().plusDays(1), requestOptions);
        Assert.assertEquals(retrievedUsage3.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage3.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage3.getRolledUpUnits().get(0).getUnitType(), unitUsageRecord.getUnitType());
        Assert.assertEquals((long) retrievedUsage3.getRolledUpUnits().get(0).getAmount(), 5);

        final RolledUpUsage retrievedUsage4 = usageApi.getAllUsage(addOnSubscriptionId, clock.getUTCToday(), clock.getUTCToday().plusDays(1), requestOptions);
        Assert.assertEquals(retrievedUsage4.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage4.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage4.getRolledUpUnits().get(0).getUnitType(), "bullets");
        Assert.assertEquals((long) retrievedUsage4.getRolledUpUnits().get(0).getAmount(), 5);

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addMonths(1);
        callbackServlet.assertListenerStatus();

        final Invoices invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, false, AuditLevel.MINIMAL, requestOptions);
        Assert.assertEquals(invoices.size(), 2);

        final InvoiceItem usageItem = Iterables.tryFind(invoices.get(1).getItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getItemType() == InvoiceItemType.USAGE;
            }
        }).orNull();
        Assert.assertNotNull(usageItem);

        Assert.assertEquals(usageItem.getPrettyPlanName(), "Bullet Monthly Plan");
        Assert.assertEquals(usageItem.getPrettyPhaseName(), "Bullet Monthly Plan Evergreen");
        Assert.assertEquals(usageItem.getPrettyUsageName(), "Bullet Usage In Arrear");
    }

    @Test(groups = "slow", description = "Test tracking ID already exists")
    public void testRecordUsageTrackingIdExists() throws Exception {

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription base = new Subscription();
        base.setAccountId(accountJson.getAccountId());
        base.setProductName("Pistol");
        base.setProductCategory(ProductCategory.BASE);
        base.setBillingPeriod(BillingPeriod.MONTHLY);
        base.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn = new Subscription();
        addOn.setAccountId(accountJson.getAccountId());
        addOn.setProductName("Bullets");
        addOn.setProductCategory(ProductCategory.ADD_ON);
        addOn.setBillingPeriod(BillingPeriod.NO_BILLING_PERIOD);
        addOn.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscriptions body = new Subscriptions();
        body.add(base);
        body.add(addOn);

        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(body,
                                                                           null,
                                                                           null,
                                                                           NULL_PLUGIN_PROPERTIES, requestOptions);
        final UUID addOnSubscriptionId = Iterables.<Subscription>find(bundle.getSubscriptions(),
                                                                      new Predicate<Subscription>() {
                                                                          @Override
                                                                          public boolean apply(final Subscription input) {
                                                                              return ProductCategory.ADD_ON.equals(input.getProductCategory());
                                                                          }
                                                                      }).getSubscriptionId();

        final UsageRecord usageRecord1 = new UsageRecord();
        usageRecord1.setAmount(10L);
        usageRecord1.setRecordDate(clock.getUTCToday().minusDays(1));

        final UnitUsageRecord unitUsageRecord = new UnitUsageRecord();
        unitUsageRecord.setUnitType("bullets");
        unitUsageRecord.setUsageRecords(ImmutableList.<UsageRecord>of(usageRecord1));

        final SubscriptionUsageRecord usage = new SubscriptionUsageRecord();
        usage.setSubscriptionId(addOnSubscriptionId);
        usage.setTrackingId(UUID.randomUUID().toString());
        usage.setUnitUsageRecords(ImmutableList.<UnitUsageRecord>of(unitUsageRecord));

        usageApi.recordUsage(usage, requestOptions);

        try {
            usageApi.recordUsage(usage, requestOptions);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.USAGE_RECORD_TRACKING_ID_ALREADY_EXISTS.getCode());
        }

    }
}
