/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.features.KillbillFeatures;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

public class TestDSTUsageIssue extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogInArrearWithRecurringAndConsumableUsage");
        allExtraProperties.put(KillbillFeatures.PROP_FEATURE_INVOICE_OPTIMIZATION, "true");
        allExtraProperties.put("org.killbill.invoice.readMaxRawUsagePreviousPeriod", "0");
        allExtraProperties.put("org.killbill.invoice.maxInvoiceLimit", "P1M");
        allExtraProperties.put("org.killbill.invoice.disable.usage.zero.amount", "true");
        allExtraProperties.put("org.killbill.invoice.item.result.behavior.mode", "DETAIL");
        allExtraProperties.put("org.killbill.invoice.usage.tz.mode", "VARIABLE");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", dataProvider = "referenceDate", alwaysRun = true)
    public void testDSTUsageIssue(final LocalDate referenceDate) throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("America/New_York");

        final DateTime referenceTime = referenceDate.toDateTimeAtStartOfDay(accountTimeZone).plusHours(4);
        clock.setTime(referenceTime);

        final AccountData accountData = getAccountData(1, accountTimeZone, referenceTime);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        // create subscription
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("trebuchet-usage-in-arrear");
        final UUID subscriptionId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, 1, null, null, null), "bundleKey", LocalDate.parse("2023-03-01"), LocalDate.parse("2023-03-01"), false, true, Collections.emptyList(), callContext);
        SubscriptionBundle bundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey("bundleKey", callContext);

        final List<DateTime> usageDates = List.of(
                new DateTime(2023, 3, 1, 4, 0, accountTimeZone),    // ** First month, the usage hour needs to be >= referenceTime hour, after that it's ok if it's 0... could be an issue?
                new DateTime(2023, 3, 15, 0, 0, accountTimeZone),
                new DateTime(2023, 3, 31, 23, 59, accountTimeZone),
                new DateTime(2023, 4, 1, 0, 0, accountTimeZone),
                new DateTime(2023, 4, 1, 0, 1, accountTimeZone),
                new DateTime(2023, 4, 15, 0, 0, accountTimeZone),
                new DateTime(2023, 4, 30, 23, 59, accountTimeZone),
                new DateTime(2023, 5, 1, 0, 0, accountTimeZone),
                new DateTime(2023, 5, 1, 0, 1, accountTimeZone));

        for (DateTime d : usageDates) {
            recordUsageData(subscriptionId, d.toString(), "stones", d, BigDecimal.ONE, callContext);
        }

        // generate march invoice - expecting usage qty = 0
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        Invoice marchInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), LocalDate.parse("2023-04-01"), Collections.emptyList(), callContext);
        invoiceUserApi.commitInvoice(marchInvoice.getId(), callContext);
        marchInvoice = invoiceUserApi.getInvoice(marchInvoice.getId(), callContext);

        // generate april invoice - expecting usage qty = 4
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        Invoice aprilInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), LocalDate.parse("2023-05-01"), Collections.emptyList(), callContext);
        invoiceUserApi.commitInvoice(aprilInvoice.getId(), callContext);
        aprilInvoice = invoiceUserApi.getInvoice(aprilInvoice.getId(), callContext);

        // generate may invoice - expecting usage qty = 2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        Invoice mayInvoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), LocalDate.parse("2023-06-01"), Collections.emptyList(), callContext);
        invoiceUserApi.commitInvoice(mayInvoice.getId(), callContext);
        mayInvoice = invoiceUserApi.getInvoice(mayInvoice.getId(), callContext);

        // verify invoices
        final InvoiceItem marchUsageItem = getUsageItem(marchInvoice);
        final InvoiceItem aprilUsageItem = getUsageItem(aprilInvoice);
        final InvoiceItem mayUsageItem = getUsageItem(mayInvoice);

        final SoftAssert softAssert = new SoftAssert();

        softAssert.assertTrue(marchUsageItem != null && marchUsageItem.getQuantity().compareTo(new BigDecimal("3")) == 0, "March qty is 3.");
        softAssert.assertTrue(marchInvoice.getTrackingIds().containsAll(List.of("2023-03-01T04:00:00.000-05:00", "2023-03-15T00:00:00.000-04:00", "2023-03-31T23:59:00.000-04:00")), "March tracking ids match.");

        softAssert.assertTrue(aprilUsageItem != null && aprilUsageItem.getQuantity().compareTo(new BigDecimal("4")) == 0, "April qty is 4.");
        softAssert.assertTrue(aprilInvoice.getTrackingIds().containsAll(List.of("2023-04-01T00:00:00.000-04:00", "2023-04-01T00:01:00.000-04:00", "2023-04-15T00:00:00.000-04:00", "2023-04-30T23:59:00.000-04:00")), "April tracking ids match.");

        softAssert.assertTrue(mayUsageItem != null && mayUsageItem.getQuantity().compareTo(new BigDecimal("2")) == 0, "May qty is 2.");
        softAssert.assertTrue(mayInvoice.getTrackingIds().containsAll(List.of("2023-05-01T00:00:00.000-04:00", "2023-05-01T00:01:00.000-04:00")), "May tracking ids match.");

        softAssert.assertAll();
    }

    @DataProvider(name = "referenceDate")
    private LocalDate[] getReferenceDates() {
        return new LocalDate[]{
                LocalDate.parse("2023-03-15"),  // during DST
                LocalDate.parse("2023-03-01")   // before DST
        };
    }

    private InvoiceItem getUsageItem(final Invoice invoice) {
        return invoice.getInvoiceItems().stream().filter(i -> i.getInvoiceItemType() == InvoiceItemType.USAGE).findFirst().orElse(null);
    }

}
