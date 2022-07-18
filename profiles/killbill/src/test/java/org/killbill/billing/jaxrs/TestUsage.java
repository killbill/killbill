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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
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

public class TestUsage extends TestJaxrsBase {

    private  static Subscriptions createSubscriptions(final Account accountJson) {
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

        final Subscriptions subscriptions = new Subscriptions();
        subscriptions.add(base);
        subscriptions.add(addOn);

        return subscriptions;
    }

    private static UUID findSubscriptionIdByProductCategory(final List<Subscription> subscriptions, final ProductCategory category) {
        return subscriptions.stream()
                            .filter(subscription -> subscription.getProductCategory().equals(category))
                            .findFirst().orElseThrow(() -> new RuntimeException("Cannot find subscriptionId in TestUsage"))
                            .getSubscriptionId();
    }

    private static InvoiceItem findInvoiceItemByUsage(final Invoices invoices, final InvoiceItemType invoiceItemType) {
        return invoices.get(1).getItems()
                       .stream()
                       .filter(invoiceItem -> invoiceItem.getItemType() == invoiceItemType)
                       .findFirst()
                       .orElse(null);
    }

    @Test(groups = "slow", description = "Can record and retrieve usage data")
    public void testRecordUsage() throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        final Subscriptions body = createSubscriptions(accountJson);

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.INVOICE_CREATION);

        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(body, (LocalDate) null, (LocalDate) null, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        final UUID addOnSubscriptionId = findSubscriptionIdByProductCategory(bundle.getSubscriptions(), ProductCategory.ADD_ON);

        clock.addDays(1);

        final UsageRecord usageRecord1 = new UsageRecord(clock.getUTCNow().minusDays(1), BigDecimal.TEN);
        final UsageRecord usageRecord2 = new UsageRecord(clock.getUTCNow(), new BigDecimal("5"));
        final UnitUsageRecord unitUsageRecord = new UnitUsageRecord("bullets", List.of(usageRecord1, usageRecord2));
        final SubscriptionUsageRecord usage = new SubscriptionUsageRecord(addOnSubscriptionId, null, List.of(unitUsageRecord));

        usageApi.recordUsage(usage, requestOptions);
        callbackServlet.assertListenerStatus();
        final RolledUpUsage retrievedUsage1 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(retrievedUsage1.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage1.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage1.getRolledUpUnits().get(0).getUnitType(), unitUsageRecord.getUnitType());
        // endDate is excluded
        Assert.assertEquals(BigDecimal.TEN.compareTo(retrievedUsage1.getRolledUpUnits().get(0).getAmount()), 0);

        final RolledUpUsage retrievedUsage2 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday().plusDays(1), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(retrievedUsage2.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage2.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage2.getRolledUpUnits().get(0).getUnitType(), unitUsageRecord.getUnitType());
        Assert.assertEquals(new BigDecimal("15").compareTo(retrievedUsage2.getRolledUpUnits().get(0).getAmount()), 0);

        final RolledUpUsage retrievedUsage3 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday(), clock.getUTCToday().plusDays(1), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(retrievedUsage3.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage3.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage3.getRolledUpUnits().get(0).getUnitType(), unitUsageRecord.getUnitType());
        Assert.assertEquals(new BigDecimal("5").compareTo(retrievedUsage3.getRolledUpUnits().get(0).getAmount()), 0);

        final RolledUpUsage retrievedUsage4 = usageApi.getAllUsage(addOnSubscriptionId, clock.getUTCToday(), clock.getUTCToday().plusDays(1), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(retrievedUsage4.getSubscriptionId(), usage.getSubscriptionId());
        Assert.assertEquals(retrievedUsage4.getRolledUpUnits().size(), 1);
        Assert.assertEquals(retrievedUsage4.getRolledUpUnits().get(0).getUnitType(), "bullets");
        Assert.assertEquals(new BigDecimal("5").compareTo(retrievedUsage4.getRolledUpUnits().get(0).getAmount()), 0);

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addMonths(1);
        callbackServlet.assertListenerStatus();

        final Invoices invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, null, AuditLevel.MINIMAL, requestOptions);
        Assert.assertEquals(invoices.size(), 2);
        // Verify system assigned one tracking ID and this is correctly returned
        Assert.assertEquals(invoices.get(1).getTrackingIds().size(), 1);

        final InvoiceItem usageItem = findInvoiceItemByUsage(invoices, InvoiceItemType.USAGE);

        Assert.assertNotNull(usageItem);

        Assert.assertEquals(usageItem.getPrettyPlanName(), "Bullet Monthly Plan");
        Assert.assertEquals(usageItem.getPrettyPhaseName(), "Bullet Monthly Plan Evergreen");
        Assert.assertEquals(usageItem.getPrettyUsageName(), "Bullet Usage In Arrear");
    }

    @Test(groups = "slow", description = "Test tracking ID already exists")
    public void testRecordUsageTrackingIdExists() throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        final Subscriptions body = createSubscriptions(accountJson);

        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(body, (LocalDate) null, (LocalDate) null, NULL_PLUGIN_PROPERTIES, requestOptions);
        final UUID addOnSubscriptionId = findSubscriptionIdByProductCategory(bundle.getSubscriptions(), ProductCategory.ADD_ON);

        final UsageRecord usageRecord1 = new UsageRecord(clock.getUTCNow().minusDays(1), BigDecimal.TEN);
        final UnitUsageRecord unitUsageRecord = new UnitUsageRecord("bullets", List.of(usageRecord1));
        final SubscriptionUsageRecord usage = new SubscriptionUsageRecord(addOnSubscriptionId,
                                                                          UUID.randomUUID().toString(),
                                                                          List.of(unitUsageRecord));

        usageApi.recordUsage(usage, requestOptions);

        try {
            usageApi.recordUsage(usage, requestOptions);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.USAGE_RECORD_TRACKING_ID_ALREADY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testRecordUsageWithDecimal() throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        final Subscriptions body = createSubscriptions(accountJson);
        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(body, (LocalDate) null, (LocalDate) null, NULL_PLUGIN_PROPERTIES, requestOptions);
        final UUID addOnSubscriptionId = findSubscriptionIdByProductCategory(bundle.getSubscriptions(), ProductCategory.ADD_ON);

        clock.addDays(2);

        final UsageRecord record1 = new UsageRecord(clock.getUTCNow().minusDays(1), new BigDecimal("4.75"));
        final UsageRecord record2 = new UsageRecord(clock.getUTCNow(), new BigDecimal("6.25"));
        final UsageRecord record3 = new UsageRecord(clock.getUTCNow().plusDays(1), new BigDecimal("8.5"));

        final UnitUsageRecord unitUsageRecord = new UnitUsageRecord("bullets", List.of(record1, record2, record3));
        final SubscriptionUsageRecord usage = new SubscriptionUsageRecord(addOnSubscriptionId, null, List.of(unitUsageRecord));

        usageApi.recordUsage(usage, requestOptions);

        final RolledUpUsage retrievedUsage1 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday().plusDays(1), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(new BigDecimal("11").compareTo(retrievedUsage1.getRolledUpUnits().get(0).getAmount()), 0);

        final RolledUpUsage retrievedUsage2 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday().plusDays(2), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(new BigDecimal("19.5").compareTo(retrievedUsage2.getRolledUpUnits().get(0).getAmount()), 0);

        final RolledUpUsage retrievedUsage3 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday(), clock.getUTCToday().plusDays(2), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(new BigDecimal("14.75").compareTo(retrievedUsage3.getRolledUpUnits().get(0).getAmount()), 0);
    }

    @Test(groups = "slow")
    public void testRecordUsageWithBigDecimalValue() throws Exception {
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        final Subscriptions body = createSubscriptions(accountJson);
        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(body, (LocalDate) null, (LocalDate) null, NULL_PLUGIN_PROPERTIES, requestOptions);
        final UUID addOnSubscriptionId = findSubscriptionIdByProductCategory(bundle.getSubscriptions(), ProductCategory.ADD_ON);

        clock.addDays(2);

        final UsageRecord record1 = new UsageRecord(clock.getUTCNow().minusDays(1), new BigDecimal("111111111.111111111"));
        final UsageRecord record2 = new UsageRecord(clock.getUTCNow(), new BigDecimal("222222222.222222222"));
        final UsageRecord record3 = new UsageRecord(clock.getUTCNow().plusDays(1), new BigDecimal("333333333.333333333"));

        final UnitUsageRecord unitUsageRecord = new UnitUsageRecord("bullets", List.of(record1, record2, record3));
        final SubscriptionUsageRecord usage = new SubscriptionUsageRecord(addOnSubscriptionId, null, List.of(unitUsageRecord));

        usageApi.recordUsage(usage, requestOptions);

        final RolledUpUsage retrievedUsage1 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday().plusDays(1), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(new BigDecimal("333333333.333333333").compareTo(retrievedUsage1.getRolledUpUnits().get(0).getAmount()), 0);

        final RolledUpUsage retrievedUsage2 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday().minusDays(1), clock.getUTCToday().plusDays(2), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(new BigDecimal("666666666.666666666").compareTo(retrievedUsage2.getRolledUpUnits().get(0).getAmount()), 0);

        final RolledUpUsage retrievedUsage3 = usageApi.getUsage(addOnSubscriptionId, unitUsageRecord.getUnitType(), clock.getUTCToday(), clock.getUTCToday().plusDays(2), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(new BigDecimal("555555555.555555555").compareTo(retrievedUsage3.getRolledUpUnits().get(0).getAmount()), 0);
    }
}
