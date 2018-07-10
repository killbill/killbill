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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.JaxrsResource;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.InvoiceItems;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.Credit;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.InvoiceDryRun;
import org.killbill.billing.client.model.gen.InvoiceItem;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestInvoice extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can search and retrieve invoices with and without items")
    public void testInvoiceOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final Invoices invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.FULL, requestOptions);
        assertEquals(invoices.size(), 2);
        for (final Invoice invoiceJson : invoices) {
            Assert.assertEquals(invoiceJson.getAuditLogs().size(), 1);
            final AuditLog auditLogJson = invoiceJson.getAuditLogs().get(0);
            Assert.assertEquals(auditLogJson.getChangeType(), "INSERT");
            Assert.assertEquals(auditLogJson.getChangedBy(), "SubscriptionBaseTransition");
            Assert.assertFalse(auditLogJson.getChangeDate().isBefore(initialDate));
            Assert.assertNotNull(auditLogJson.getUserToken());
            Assert.assertNull(auditLogJson.getReasonCode());
            Assert.assertNull(auditLogJson.getComments());
        }

        final Invoice invoiceJson = invoices.get(0);
        assertEquals(invoiceJson.getItems().size(), 1);
        final InvoiceItem invoiceItem = invoiceJson.getItems().get(0);
        assertEquals(invoiceItem.getProductName(), "Shotgun");
        assertEquals(invoiceItem.getPrettyProductName(), "Shotgun");
        assertEquals(invoiceItem.getPlanName(), "shotgun-monthly");
        assertEquals(invoiceItem.getPrettyPlanName(), "Shotgun Monthly");
        assertEquals(invoiceItem.getPhaseName(), "shotgun-monthly-trial");
        assertEquals(invoiceItem.getPrettyPhaseName(), "shotgun-monthly-trial");

        // Check get with & without items
        assertTrue(invoiceApi.getInvoice(invoiceJson.getInvoiceId(), Boolean.FALSE, Boolean.FALSE, AuditLevel.NONE, requestOptions).getItems().isEmpty());
        assertTrue(invoiceApi.getInvoiceByNumber(Integer.valueOf(invoiceJson.getInvoiceNumber()), Boolean.FALSE, Boolean.FALSE, AuditLevel.NONE, requestOptions).getItems().isEmpty());
        assertEquals(invoiceApi.getInvoice(invoiceJson.getInvoiceId(), Boolean.TRUE, Boolean.FALSE, AuditLevel.NONE, requestOptions).getItems().size(), invoiceJson.getItems().size());
        assertEquals(invoiceApi.getInvoiceByNumber(Integer.valueOf(invoiceJson.getInvoiceNumber()), Boolean.TRUE, Boolean.FALSE, AuditLevel.NONE, requestOptions).getItems().size(), invoiceJson.getItems().size());
        assertEquals(invoiceApi.getInvoiceByItemId(invoiceItem.getInvoiceItemId(), true, false, AuditLevel.NONE, requestOptions).getItems().size(), invoiceJson.getItems().size() );


        // Check we can retrieve an individual invoice
        final Invoice firstInvoice = invoiceApi.getInvoice(invoiceJson.getInvoiceId(), true, false, AuditLevel.FULL, requestOptions);
        assertEquals(firstInvoice, invoiceJson);

        // Check we can retrieve the invoice by number
        final Invoice firstInvoiceByNumberJson = invoiceApi.getInvoiceByNumber(Integer.valueOf(invoiceJson.getInvoiceNumber()), true, false, AuditLevel.FULL, requestOptions);
        assertEquals(firstInvoiceByNumberJson, invoiceJson);

        // Check we can retrieve the HTML version
        final String htmlInvoice = invoiceApi.getInvoiceAsHTML(invoiceJson.getInvoiceId(), requestOptions);
        assertEquals(htmlInvoice, "<html>\n" +
                                  "    <head>\n" +
                                  "        <style type=\"text/css\">\n" +
                                  "            th {align=left; width=225px; border-bottom: solid 2px black;}\n" +
                                  "        </style>\n" +
                                  "    </head>\n" +
                                  "    <body>\n" +
                                  "        <h1>invoiceTitle</h1>\n" +
                                  "        <table>\n" +
                                  "            <tr>\n" +
                                  "                <td rowspan=3 width=350px>Insert image here</td>\n" +
                                  "                <td width=100px/>\n" +
                                  "                <td width=225px/>\n" +
                                  "                <td width=225px/>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td />\n" +
                                  "                <td align=right>invoiceDate</td>\n" +
                                  "                <td>25 avr. 2012</td>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td />\n" +
                                  "                <td align=right>invoiceNumber</td>\n" +
                                  "                <td>" + invoiceJson.getInvoiceNumber() + "</td>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td>companyName</td>\n" +
                                  "                <td></td>\n" +
                                  "                <td align=right>accountOwnerName</td>\n" +
                                  "                <td>" + accountJson.getName() + "</td>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td>companyAddress</td>\n" +
                                  "                <td />\n" +
                                  "                <td />\n" +
                                  "                <td>" + accountJson.getEmail() + "</td>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td>companyCityProvincePostalCode</td>\n" +
                                  "                <td />\n" +
                                  "                <td />\n" +
                                  "                <td>" + accountJson.getPhone() + "</td>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td>companyCountry</td>\n" +
                                  "                <td />\n" +
                                  "                <td />\n" +
                                  "                <td />\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td><companyUrl</td>\n" +
                                  "                <td />\n" +
                                  "                <td />\n" +
                                  "                <td />\n" +
                                  "            </tr>\n" +
                                  "        </table>\n" +
                                  "        <br />\n" +
                                  "        <br />\n" +
                                  "        <br />\n" +
                                  "        <table>\n" +
                                  "            <tr>\n" +
                                  "                <th>invoiceItemBundleName</td>\n" +
                                  "                <th>invoiceItemDescription</td>\n" +
                                  "                <th>invoiceItemServicePeriod</td>\n" +
                                  "                <th>invoiceItemAmount</td>\n" +
                                  "            </tr>\n" +
                                  "            \n" +
                                  "            <tr>\n" +
                                  "                <td>shotgun-monthly-trial</td>\n" +
                                  "                <td>Monthly shotgun plan</td>\n" +
                                  "                <td>25 avr. 2012</td>\n" +
                                  "                <td>USD 0.00</td>\n" +
                                  "            </tr>\n" +
                                  "            \n" +
                                  "            <tr>\n" +
                                  "                <td colspan=4 />\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td colspan=2 />\n" +
                                  "                <td align=right><strong>invoiceAmount</strong></td>\n" +
                                  "                <td align=right><strong>0.00</strong></td>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td colspan=2 />\n" +
                                  "                <td align=right><strong>invoiceAmountPaid</strong></td>\n" +
                                  "                <td align=right><strong>0.00</strong></td>\n" +
                                  "            </tr>\n" +
                                  "            <tr>\n" +
                                  "                <td colspan=2 />\n" +
                                  "                <td align=right><strong>invoiceBalance</strong></td>\n" +
                                  "                <td align=right><strong>0.00</strong></td>\n" +
                                  "            </tr>\n" +
                                  "        </table>\n" +
                                  "    </body>\n" +
                                  "</html>\n" +
                                  "\n");

        // Then create a dryRun for next upcoming invoice
        final InvoiceDryRun dryRunArg = new InvoiceDryRun().setDryRunType(DryRunType.UPCOMING_INVOICE);

        final Invoice dryRunInvoice = invoiceApi.generateDryRunInvoice(dryRunArg, accountJson.getAccountId(), null, requestOptions);
        assertEquals(dryRunInvoice.getBalance(), new BigDecimal("249.95"));
        assertEquals(dryRunInvoice.getTargetDate(), new LocalDate(2012, 6, 25));
        assertEquals(dryRunInvoice.getItems().size(), 1);
        assertEquals(dryRunInvoice.getItems().get(0).getStartDate(), new LocalDate(2012, 6, 25));
        assertEquals(dryRunInvoice.getItems().get(0).getEndDate(), new LocalDate(2012, 7, 25));
        assertEquals(dryRunInvoice.getItems().get(0).getAmount(), new BigDecimal("249.95"));

        final LocalDate futureDate = dryRunInvoice.getTargetDate();
        // The one more time with no DryRun
        invoiceApi.createFutureInvoice(accountJson.getAccountId(), futureDate, requestOptions);

        // Check again # invoices, should be 3 this time
        final List<Invoice> newInvoiceList = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        assertEquals(newInvoiceList.size(), 3);
    }

    @Test(groups = "slow", description = "Can create a subscription in dryRun mode and get an invoice back")
    public void testDryRunSubscriptionCreate() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        // "Assault-Rifle", BillingPeriod.ANNUAL, "rescue", BillingActionPolicy.IMMEDIATE,
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        final InvoiceDryRun dryRunArg = new InvoiceDryRun(DryRunType.SUBSCRIPTION_ACTION, SubscriptionEventType.START_BILLING,
                                                          null, "Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, null, null, null, null, null, null);

        final Invoice dryRunInvoice = invoiceApi.generateDryRunInvoice(dryRunArg, accountJson.getAccountId(), new LocalDate(initialDate, DateTimeZone.forID(accountJson.getTimeZone())), requestOptions);
        assertEquals(dryRunInvoice.getItems().size(), 1);

    }

    @Test(groups = "slow", description = "Can retrieve invoice payments")
    public void testInvoicePayments() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        assertEquals(invoices.size(), 2);

        final Invoice invoiceWithPositiveAmount = Iterables.tryFind(invoices, new Predicate<Invoice>() {
            @Override
            public boolean apply(final Invoice input) {
                return input.getAmount().compareTo(BigDecimal.ZERO) > 0;
            }
        }).orNull();
        Assert.assertNotNull(invoiceWithPositiveAmount);

        final InvoicePayments objFromJson = invoiceApi.getPaymentsForInvoice(invoiceWithPositiveAmount.getInvoiceId(), requestOptions);
        assertEquals(objFromJson.size(), 1);
        assertEquals(invoiceWithPositiveAmount.getAmount().compareTo(objFromJson.get(0).getPurchasedAmount()), 0);
    }

    @Test(groups = "slow", description = "Can pay invoices")
    public void testPayAllInvoices() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        // No payment method
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Check there was no payment made
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), null, requestOptions).size(), 0);

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        assertEquals(invoices.size(), 2);
        final Invoice invoiceToPay = invoices.get(1);
        assertEquals(invoiceToPay.getBalance().compareTo(BigDecimal.ZERO), 1);


        // Pay all invoices
        accountApi.payAllInvoices(accountJson.getAccountId(), null, true, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        for (final Invoice invoice : accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions)) {
            assertEquals(invoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        }
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), null, requestOptions).size(), 1);
    }

    @Test(groups = "slow", description = "Can create an insta-payment")
    public void testInvoiceCreatePayment() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        // STEPH MISSING SET ACCOUNT AUTO_PAY_OFF
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        assertEquals(invoices.size(), 2);

        for (final Invoice cur : invoices) {
            if (cur.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // CREATE PAYMENT
            final InvoicePayment invoicePayment = new InvoicePayment();
            invoicePayment.setPurchasedAmount(cur.getBalance());
            invoicePayment.setAccountId(accountJson.getAccountId());
            invoicePayment.setTargetInvoiceId(cur.getInvoiceId());
            final InvoicePayment objFromJson = invoiceApi.createInstantPayment(cur.getInvoiceId(), invoicePayment, true, null, requestOptions);
            assertEquals(cur.getBalance().compareTo(objFromJson.getPurchasedAmount()), 0);
        }
    }

    @Test(groups = "slow", description = "Can create an external payment")
    public void testExternalPayment() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Verify we didn't get any invoicePayment
        final List<InvoicePayment> noPaymentsFromJson = accountApi.getInvoicePayments(accountJson.getAccountId(), null, requestOptions);
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final UUID invoiceId = invoices.get(1).getInvoiceId();

        // Post an external invoicePayment
        final InvoicePayment invoicePayment = new InvoicePayment();
        invoicePayment.setPurchasedAmount(BigDecimal.TEN);
        invoicePayment.setAccountId(accountJson.getAccountId());
        invoicePayment.setTargetInvoiceId(invoiceId);
        invoiceApi.createInstantPayment(invoiceId, invoicePayment, true, null, requestOptions);

        // Verify we indeed got the invoicePayment
        final List<InvoicePayment> paymentsFromJson = accountApi.getInvoicePayments(accountJson.getAccountId(), null, requestOptions);
        assertEquals(paymentsFromJson.size(), 1);
        assertEquals(paymentsFromJson.get(0).getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(paymentsFromJson.get(0).getTargetInvoiceId(), invoiceId);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final UUID paymentMethodId = paymentsFromJson.get(0).getPaymentMethodId();
        final PaymentMethod paymentMethodJson = paymentMethodApi.getPaymentMethod(paymentMethodId, null, requestOptions);
        assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());
        assertEquals(paymentMethodJson.getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        assertNull(paymentMethodJson.getPluginInfo());
    }

    @Test(groups = "slow", description = "Can fully adjust an invoice item")
    public void testFullInvoiceItemAdjustment() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final Invoice invoice = invoices.get(1);
        // Verify the invoice we picked is non zero
        assertEquals(invoice.getAmount().compareTo(BigDecimal.ZERO), 1);
        final InvoiceItem invoiceItem = invoice.getItems().get(0);
        // Verify the item we picked is non zero
        assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Adjust the full amount
        final InvoiceItem adjustmentInvoiceItem = new InvoiceItem();
        adjustmentInvoiceItem.setAccountId(accountJson.getAccountId());
        adjustmentInvoiceItem.setInvoiceId(invoice.getInvoiceId());
        adjustmentInvoiceItem.setInvoiceItemId(invoiceItem.getInvoiceItemId());
        final String itemDetails = "{\n" +
                                   "  \"user\": \"admin\",\n" +
                                   "  \"reason\": \"SLA not met\"\n" +
                                   "}";
        adjustmentInvoiceItem.setItemDetails(itemDetails);
        invoiceApi.adjustInvoiceItem(invoice.getInvoiceId(), adjustmentInvoiceItem, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Verify the new invoice balance is zero
        final Invoice adjustedInvoice = invoiceApi.getInvoice(invoice.getInvoiceId(), true, false, AuditLevel.FULL, requestOptions);
        assertEquals(adjustedInvoice.getAmount().compareTo(BigDecimal.ZERO), 0);

        final InvoiceItem createdAdjustment = Iterables.find(adjustedInvoice.getItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return InvoiceItemType.ITEM_ADJ.equals(input.getItemType());
            }
        });
        assertEquals(createdAdjustment.getItemDetails(), itemDetails);

        // Verify invoice audit logs
        Assert.assertEquals(adjustedInvoice.getAuditLogs().size(), 1);
        final AuditLog invoiceAuditLogJson = adjustedInvoice.getAuditLogs().get(0);
        Assert.assertEquals(invoiceAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(invoiceAuditLogJson.getChangedBy(), "SubscriptionBaseTransition");
        Assert.assertNotNull(invoiceAuditLogJson.getChangeDate());
        Assert.assertNotNull(invoiceAuditLogJson.getUserToken());
        Assert.assertNull(invoiceAuditLogJson.getReasonCode());
        Assert.assertNull(invoiceAuditLogJson.getComments());

        Assert.assertEquals(adjustedInvoice.getItems().size(), 2);

        // Verify invoice items audit logs

        // The first item is the original item
        Assert.assertEquals(adjustedInvoice.getItems().get(0).getAuditLogs().size(), 1);
        final AuditLog itemAuditLogJson = adjustedInvoice.getItems().get(0).getAuditLogs().get(0);
        Assert.assertEquals(itemAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(itemAuditLogJson.getChangedBy(), "SubscriptionBaseTransition");
        Assert.assertNotNull(itemAuditLogJson.getChangeDate());
        Assert.assertNotNull(itemAuditLogJson.getUserToken());
        Assert.assertNull(itemAuditLogJson.getReasonCode());
        Assert.assertNull(itemAuditLogJson.getComments());

        // The second one is the adjustment
        Assert.assertEquals(adjustedInvoice.getItems().get(1).getAuditLogs().size(), 1);
        final AuditLog adjustedItemAuditLogJson = adjustedInvoice.getItems().get(1).getAuditLogs().get(0);
        Assert.assertEquals(adjustedItemAuditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(adjustedItemAuditLogJson.getChangedBy(), createdBy);
        Assert.assertEquals(adjustedItemAuditLogJson.getReasonCode(), reason);
        Assert.assertEquals(adjustedItemAuditLogJson.getComments(), comment);
        Assert.assertNotNull(adjustedItemAuditLogJson.getChangeDate());
        Assert.assertNotNull(adjustedItemAuditLogJson.getUserToken());
    }

    @Test(groups = "slow", description = "Can partially adjust an invoice item")
    public void testPartialInvoiceItemAdjustment() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final Invoice invoice = invoices.get(1);
        // Verify the invoice we picked is non zero
        assertEquals(invoice.getAmount().compareTo(BigDecimal.ZERO), 1);
        final InvoiceItem invoiceItem = invoice.getItems().get(0);
        // Verify the item we picked is non zero
        assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Adjust partially the item
        final BigDecimal adjustedAmount = invoiceItem.getAmount().divide(BigDecimal.TEN, BigDecimal.ROUND_HALF_UP);
        final InvoiceItem adjustmentInvoiceItem = new InvoiceItem();
        adjustmentInvoiceItem.setAccountId(accountJson.getAccountId());
        adjustmentInvoiceItem.setInvoiceId(invoice.getInvoiceId());
        adjustmentInvoiceItem.setInvoiceItemId(invoiceItem.getInvoiceItemId());
        adjustmentInvoiceItem.setAmount(adjustedAmount);
        adjustmentInvoiceItem.setCurrency(invoice.getCurrency());
        invoiceApi.adjustInvoiceItem(invoice.getInvoiceId(), adjustmentInvoiceItem, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Verify the new invoice balance
        final Invoice adjustedInvoice = invoiceApi.getInvoice(invoice.getInvoiceId(), requestOptions);
        final BigDecimal adjustedInvoiceBalance = invoice.getBalance().add(adjustedAmount.negate()).setScale(2, BigDecimal.ROUND_HALF_UP);
        assertEquals(adjustedInvoice.getBalance().compareTo(adjustedInvoiceBalance), 0, String.format("Adjusted invoice balance is %s, should be %s", adjustedInvoice.getBalance(), adjustedInvoiceBalance));
    }

    @Test(groups = "slow", description = "Can create an external charge")
    public void testExternalChargeOnNewInvoice() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final Invoices originalInvoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        assertEquals(originalInvoices.size(), 2);

        final UUID firstInvoiceItemId = originalInvoices.get(0).getItems().get(0).getInvoiceItemId();

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(accountJson.getCurrency());
        externalCharge.setDescription(UUID.randomUUID().toString());
        externalCharge.setItemDetails("Item Details");
        externalCharge.setLinkedInvoiceItemId(firstInvoiceItemId);

        final LocalDate startDate = clock.getUTCToday();
        externalCharge.setStartDate(startDate);
        final LocalDate endDate = startDate.plusDays(10);
        externalCharge.setEndDate(endDate);

        final InvoiceItems itemsForCharge = new InvoiceItems();
        itemsForCharge.add(externalCharge);

        final List<InvoiceItem> createdExternalCharges = invoiceApi.createExternalCharges(accountJson.getAccountId(), itemsForCharge, clock.getUTCToday(),  true, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(createdExternalCharges.size(), 1);
        final Invoice invoiceWithItems = invoiceApi.getInvoice(createdExternalCharges.get(0).getInvoiceId(), true, false, AuditLevel.NONE, requestOptions);
        assertEquals(invoiceWithItems.getBalance().compareTo(chargeAmount), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertEquals(invoiceWithItems.getItems().get(0).getDescription(), externalCharge.getDescription());
        assertNull(invoiceWithItems.getItems().get(0).getBundleId());
        assertEquals(invoiceWithItems.getItems().get(0).getStartDate().compareTo(startDate), 0);
        assertEquals(invoiceWithItems.getItems().get(0).getEndDate().compareTo(endDate), 0);
        assertEquals(invoiceWithItems.getItems().get(0).getItemDetails(), "Item Details");
        assertEquals(invoiceWithItems.getItems().get(0).getLinkedInvoiceItemId(), firstInvoiceItemId);

        // Verify the total number of invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions).size(), 3);
    }

    @Test(groups = "slow", description = "Can create multiple external charges")
    public void testExternalCharges() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;

        final InvoiceItems externalCharges = new InvoiceItems();

        // Does not pass currency to test on purpose that we will default to account currency
        final InvoiceItem externalCharge1 = new InvoiceItem();
        externalCharge1.setAccountId(accountJson.getAccountId());
        externalCharge1.setAmount(chargeAmount);
        externalCharge1.setDescription(UUID.randomUUID().toString());
        externalCharges.add(externalCharge1);

        final InvoiceItem externalCharge2 = new InvoiceItem();
        externalCharge2.setAccountId(accountJson.getAccountId());
        externalCharge2.setAmount(chargeAmount);
        externalCharge2.setCurrency(accountJson.getCurrency());
        externalCharge2.setDescription(UUID.randomUUID().toString());
        externalCharges.add(externalCharge2);

        final List<InvoiceItem> createdExternalCharges = invoiceApi.createExternalCharges(accountJson.getAccountId(), externalCharges, clock.getUTCToday(), true, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(createdExternalCharges.size(), 2);
        assertEquals(createdExternalCharges.get(0).getCurrency(), accountJson.getCurrency());
        assertEquals(createdExternalCharges.get(1).getCurrency(), accountJson.getCurrency());

        // Verify the total number of invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions).size(), 3);
    }

    @Test(groups = "slow", description = "Can create multiple external charges with same invoice and external keys"/* , invocationCount = 10*/)
    public void testExternalChargesWithSameInvoice() throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;

        final InvoiceItems externalCharges = new InvoiceItems();

        // Does not pass currency to test on purpose that we will default to account currency
        final InvoiceItem externalCharge1 = new InvoiceItem();
        externalCharge1.setAccountId(accountJson.getAccountId());
        externalCharge1.setAmount(chargeAmount);
        externalCharge1.setDescription(UUID.randomUUID().toString());
        externalCharges.add(externalCharge1);

        final InvoiceItem externalCharge2 = new InvoiceItem();
        externalCharge2.setAccountId(accountJson.getAccountId());
        externalCharge2.setAmount(chargeAmount);
        externalCharge2.setCurrency(accountJson.getCurrency());
        externalCharge2.setDescription(UUID.randomUUID().toString());
        externalCharges.add(externalCharge2);


        final List<InvoiceItem> createdExternalCharges = invoiceApi.createExternalCharges(accountJson.getAccountId(), externalCharges, clock.getUTCToday(), true, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(createdExternalCharges.size(), 2);
        assertEquals(createdExternalCharges.get(0).getCurrency(), accountJson.getCurrency());
        assertEquals(createdExternalCharges.get(1).getCurrency(), accountJson.getCurrency());
    }


    @Test(groups = "slow", description = "Can create an external charge for a bundle")
    public void testExternalChargeForBundleOnNewInvoice() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions).size(), 2);

        // Post an external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setCurrency(accountJson.getCurrency());
        externalCharge.setBundleId(bundleId);
        final InvoiceItems input = new InvoiceItems();
        input.add(externalCharge);
        final List<InvoiceItem> createdExternalCharges = invoiceApi.createExternalCharges(accountJson.getAccountId(), input, clock.getUTCToday(), true, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(createdExternalCharges.size(), 1);
        final Invoice invoiceWithItems = invoiceApi.getInvoice(createdExternalCharges.get(0).getInvoiceId(), true, null, AuditLevel.NONE, requestOptions);
        assertEquals(invoiceWithItems.getBalance().compareTo(chargeAmount), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertEquals(invoiceWithItems.getItems().get(0).getBundleId(), bundleId);

        // Verify the total number of invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions).size(), 3);
    }

    @Test(groups = "slow", description = "Can create tax items for a bundle")
    public void testAddTaxItemsOnNewInvoice() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions).size(), 2);

        // Post an external charge
        final BigDecimal taxAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem taxItem = new InvoiceItem();
        taxItem.setAccountId(accountJson.getAccountId());
        taxItem.setAmount(taxAmount);
        taxItem.setCurrency(accountJson.getCurrency());
        taxItem.setBundleId(bundleId);
        final InvoiceItems input = new InvoiceItems();
        input.add(taxItem);
        final List<InvoiceItem> createdTaxItems = invoiceApi.createTaxItems(accountJson.getAccountId(), input, true, clock.getUTCToday(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(createdTaxItems.size(), 1);
        final Invoice invoiceWithItems = invoiceApi.getInvoice(createdTaxItems.get(0).getInvoiceId(), true, null, AuditLevel.NONE, requestOptions);
        assertEquals(invoiceWithItems.getBalance().compareTo(taxAmount), 0);
        assertEquals(invoiceWithItems.getItems().size(), 1);
        assertEquals(invoiceWithItems.getItems().get(0).getBundleId(), bundleId);
        assertEquals(invoiceWithItems.getItems().get(0).getItemType(), InvoiceItemType.TAX);

        // Verify the total number of invoices
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions).size(), 3);
    }



    @Test(groups = "slow", description = "Can paginate and search through all invoices")
    public void testInvoicesPagination() throws Exception {
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        for (int i = 0; i < 3; i++) {
            callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_CREATION, ExtBusEventType.INVOICE_PAYMENT_SUCCESS, ExtBusEventType.PAYMENT_SUCCESS);
            clock.addMonths(1);
            callbackServlet.assertListenerStatus();
        }

        final Invoices allInvoices = invoiceApi.getInvoices(requestOptions);
        Assert.assertEquals(allInvoices.size(), 5);

        for (final Invoice invoice : allInvoices) {
            Assert.assertEquals(invoiceApi.searchInvoices(invoice.getInvoiceId().toString(), requestOptions).size(), 1);
            Assert.assertEquals(invoiceApi.searchInvoices(invoice.getAccountId().toString(), requestOptions).size(), 5);
            Assert.assertEquals(invoiceApi.searchInvoices(invoice.getInvoiceNumber().toString(), requestOptions).size(), 1);
            Assert.assertEquals(invoiceApi.searchInvoices(invoice.getCurrency().toString(), requestOptions).size(), 5);
        }

        Invoices page = invoiceApi.getInvoices(0L, 1L, false, AuditLevel.NONE, requestOptions);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allInvoices.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);
    }

    @Test(groups = "slow", description = "Can add a credit to a new invoice")
    public void testCreateCreditInvoiceAndMoveStatus() throws Exception {

        final Account account = createAccountWithDefaultPaymentMethod();

        final BigDecimal creditAmount = BigDecimal.TEN;
        final Credit credit = new Credit();
        credit.setAccountId(account.getAccountId());
        credit.setInvoiceId(null);
        credit.setCreditAmount(creditAmount);
        final Credit creditJson = creditApi.createCredit(credit, false, NULL_PLUGIN_PROPERTIES, requestOptions);

        Invoice invoice = invoiceApi.getInvoice(creditJson.getInvoiceId(), requestOptions);
        Assert.assertEquals(invoice.getStatus(), InvoiceStatus.DRAFT);

        invoiceApi.commitInvoice(invoice.getInvoiceId(), requestOptions);

        invoice = invoiceApi.getInvoice(creditJson.getInvoiceId(), requestOptions);
        Assert.assertEquals(invoice.getStatus(), InvoiceStatus.COMMITTED);
    }

    @Test(groups = "slow", description = "Can create a migration invoice")
    public void testInvoiceMigration() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, true, false, false, AuditLevel.NONE, requestOptions);
        assertEquals(invoices.size(), 2);

        // Migrate an invoice with one external charge
        final BigDecimal chargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new InvoiceItem();
        externalCharge.setStartDate(new LocalDate());
        externalCharge.setAccountId(accountJson.getAccountId());
        externalCharge.setAmount(chargeAmount);
        externalCharge.setItemType(InvoiceItemType.EXTERNAL_CHARGE);
        externalCharge.setCurrency(accountJson.getCurrency());
        final InvoiceItems inputInvoice = new InvoiceItems();
        inputInvoice.add(externalCharge);
        final Account accountWithBalance = accountApi.getAccount(accountJson.getAccountId(), true, true, AuditLevel.NONE, requestOptions);

        final Multimap<String, String> queryFollowParams = HashMultimap.<String, String>create(requestOptions.getQueryParamsForFollow());
        queryFollowParams.put(JaxrsResource.QUERY_INVOICE_WITH_ITEMS, "true");

        final Invoice migrationInvoice = invoiceApi.createMigrationInvoice(accountJson.getAccountId(), inputInvoice, null, requestOptions.extend().withQueryParamsForFollow(queryFollowParams).build());
        assertEquals(migrationInvoice.getBalance(), BigDecimal.ZERO);
        assertEquals(migrationInvoice.getItems().size(), 1);
        assertEquals(migrationInvoice.getItems().get(0).getAmount().compareTo(chargeAmount), 0);
        assertEquals(migrationInvoice.getItems().get(0).getCurrency(), accountJson.getCurrency());

        final List<Invoice> invoicesWithMigration = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, true, true, false, false, AuditLevel.NONE, requestOptions);
        assertEquals(invoicesWithMigration.size(), 3);

        final Account accountWithBalanceAfterMigration = accountApi.getAccount(accountJson.getAccountId(), true, true, AuditLevel.NONE, requestOptions);
        assertEquals(accountWithBalanceAfterMigration.getAccountBalance().compareTo(accountWithBalance.getAccountBalance()), 0);
    }

    @Test(groups = "slow", description = "Can transfer credit to parent account")
    public void testInvoiceTransferCreditToParentAccount() throws Exception {
        final Account parentAccount = createAccount();
        final Account childAccount = createAccount(parentAccount.getAccountId());

        final BigDecimal creditAmount = BigDecimal.TEN;
        final Credit credit = new Credit();
        credit.setAccountId(childAccount.getAccountId());
        credit.setInvoiceId(null);
        credit.setCreditAmount(creditAmount);

        // insert credit to child account
        final Credit creditJson = creditApi.createCredit(credit, true, NULL_PLUGIN_PROPERTIES, requestOptions);

        Invoices childInvoices = accountApi.getInvoicesForAccount(childAccount.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(childInvoices.size(), 1);
        Assert.assertEquals(childInvoices.get(0).getCreditAdj().compareTo(BigDecimal.TEN), 0);

        Invoices parentInvoices = accountApi.getInvoicesForAccount(parentAccount.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(parentInvoices.size(), 0);

        // transfer credit to parent account
        accountApi.transferChildCreditToParent(childAccount.getAccountId(), requestOptions);

        childInvoices = accountApi.getInvoicesForAccount(childAccount.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(childInvoices.size(), 2);
        Assert.assertEquals(childInvoices.get(1).getCreditAdj().compareTo(BigDecimal.TEN.negate()), 0);

        parentInvoices = accountApi.getInvoicesForAccount(parentAccount.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(parentInvoices.size(), 1);
        Assert.assertEquals(parentInvoices.get(0).getCreditAdj().compareTo(BigDecimal.TEN), 0);
    }

    @Test(groups = "slow", description = "Fail to transfer credit from an account without parent account",
            expectedExceptions = KillBillClientException.class, expectedExceptionsMessageRegExp = ".* does not have a Parent Account associated")
    public void testInvoiceTransferCreditAccountNoParent() throws Exception {
        final Account account = createAccount();

        // transfer credit to parent account
        accountApi.transferChildCreditToParent(account.getAccountId(), requestOptions);

    }

    @Test(groups = "slow", description = "Fail to transfer credit from an account without parent account",
            expectedExceptions = KillBillClientException.class, expectedExceptionsMessageRegExp = ".* does not have credit")
    public void testInvoiceTransferCreditAccountNoCredit() throws Exception {
        final Account parentAccount = createAccount();
        final Account childAccount = createAccount(parentAccount.getAccountId());

        // transfer credit to parent account
        accountApi.transferChildCreditToParent(childAccount.getAccountId(), requestOptions);

    }

    @Test(groups = "slow", description = "Can search and retrieve parent and children invoices with and without children items")
    public void testParentInvoiceWithChildItems() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account parentAccount = createAccount();
        final Account childAccount1 = createAccount(parentAccount.getAccountId());
        final Account childAccount2 = createAccount(parentAccount.getAccountId());
        final Account childAccount3 = createAccount(parentAccount.getAccountId());

        // Add a bundle, subscription and move the clock to get the first invoice
        createSubscription(childAccount1.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                           ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        createSubscription(childAccount2.getAccountId(), UUID.randomUUID().toString(), "Pistol",
                           ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        createSubscription(childAccount3.getAccountId(), UUID.randomUUID().toString(), "Shotgun",
                           ProductCategory.BASE, BillingPeriod.MONTHLY, true);

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_CREATION);
        clock.addDays(32);
        callbackServlet.assertListenerStatus();

        final List<Invoice> child1Invoices = accountApi.getInvoicesForAccount(childAccount1.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        final List<Invoice> child2Invoices = accountApi.getInvoicesForAccount(childAccount2.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        final List<Invoice> child3Invoices = accountApi.getInvoicesForAccount(childAccount3.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);

        assertEquals(child1Invoices.size(), 2);
        final Invoice child1RecurringInvoice = child1Invoices.get(1);
        final InvoiceItem child1RecurringInvoiceItem = child1RecurringInvoice.getItems().get(0);
        final InvoiceItem child2RecurringInvoiceItem = child2Invoices.get(1).getItems().get(0);
        final InvoiceItem child3RecurringInvoiceItem = child3Invoices.get(1).getItems().get(0);

        final List<Invoice> parentInvoices = accountApi.getInvoicesForAccount(parentAccount.getAccountId(), null, true, false, false, false, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoices.size(), 2);

        // check parent invoice with child invoice items and no adjustments
        // parameters: withItems = true, withChildrenItems = true
        Invoice parentInvoiceWithChildItems = invoiceApi.getInvoice(parentInvoices.get(1).getInvoiceId(), true, true, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoiceWithChildItems.getItems().size(), 3);
        assertEquals(parentInvoiceWithChildItems.getItems().get(0).getChildItems().size(), 1);
        assertEquals(parentInvoiceWithChildItems.getItems().get(1).getChildItems().size(), 1);
        assertEquals(parentInvoiceWithChildItems.getItems().get(2).getChildItems().size(), 1);

        // add an item adjustment
        final InvoiceItem adjustmentInvoiceItem = new InvoiceItem();
        adjustmentInvoiceItem.setAccountId(childAccount1.getAccountId());
        adjustmentInvoiceItem.setInvoiceId(child1RecurringInvoice.getInvoiceId());
        adjustmentInvoiceItem.setInvoiceItemId(child1RecurringInvoiceItem.getInvoiceItemId());
        adjustmentInvoiceItem.setAmount(BigDecimal.TEN);
        adjustmentInvoiceItem.setCurrency(child1RecurringInvoiceItem.getCurrency());
        final Invoice invoiceAdjustment = invoiceApi.adjustInvoiceItem(child1RecurringInvoice.getInvoiceId(), adjustmentInvoiceItem, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        final InvoiceItem child1AdjInvoiceItem = invoiceApi.getInvoice(invoiceAdjustment.getInvoiceId(), true, true, AuditLevel.NONE, requestOptions).getItems().get(1);

        // check parent invoice with child invoice items and adjustments
        // parameters: withItems = true, withChildrenItems = true
        parentInvoiceWithChildItems = invoiceApi.getInvoice(parentInvoices.get(1).getInvoiceId(), true, true, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoiceWithChildItems.getItems().size(), 3);
        assertEquals(parentInvoiceWithChildItems.getItems().get(0).getChildItems().size(), 2);
        assertEquals(parentInvoiceWithChildItems.getItems().get(1).getChildItems().size(), 1);
        assertEquals(parentInvoiceWithChildItems.getItems().get(2).getChildItems().size(), 1);

        final InvoiceItem child1InvoiceItemFromParent = parentInvoiceWithChildItems.getItems().get(0).getChildItems().get(0);
        final InvoiceItem child1AdjInvoiceItemFromParent = parentInvoiceWithChildItems.getItems().get(0).getChildItems().get(1);
        final InvoiceItem child2InvoiceItemFromParent = parentInvoiceWithChildItems.getItems().get(1).getChildItems().get(0);
        final InvoiceItem child3InvoiceItemFromParent = parentInvoiceWithChildItems.getItems().get(2).getChildItems().get(0);

        // check children items for each PARENT_SUMMARY item
        assertTrue(child1InvoiceItemFromParent.equals(child1RecurringInvoiceItem));
        assertTrue(child1AdjInvoiceItemFromParent.equals(child1AdjInvoiceItem));
        assertTrue(child2InvoiceItemFromParent.equals(child2RecurringInvoiceItem));
        assertTrue(child3InvoiceItemFromParent.equals(child3RecurringInvoiceItem));

        // check parent invoice without child invoice items
        parentInvoiceWithChildItems = invoiceApi.getInvoice(parentInvoices.get(1).getInvoiceId(), true, false, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoiceWithChildItems.getItems().size(), 3);
        assertNull(parentInvoiceWithChildItems.getItems().get(0).getChildItems());
        assertNull(parentInvoiceWithChildItems.getItems().get(1).getChildItems());
        assertNull(parentInvoiceWithChildItems.getItems().get(2).getChildItems());

        // check parent invoice without items but with child invoice items and adjustment. Should return items anyway.
        // parameters: withItems = false, withChildrenItems = true
        parentInvoiceWithChildItems = invoiceApi.getInvoice(parentInvoices.get(1).getInvoiceId(), false, true, AuditLevel.NONE, requestOptions);
        assertEquals(parentInvoiceWithChildItems.getItems().size(), 3);
        assertEquals(parentInvoiceWithChildItems.getItems().get(0).getChildItems().size(), 2);
        assertEquals(parentInvoiceWithChildItems.getItems().get(1).getChildItems().size(), 1);
        assertEquals(parentInvoiceWithChildItems.getItems().get(2).getChildItems().size(), 1);

    }

}
