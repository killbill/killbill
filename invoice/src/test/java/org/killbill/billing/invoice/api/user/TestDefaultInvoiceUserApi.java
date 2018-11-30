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

package org.killbill.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.TestInvoiceHelper.DryRunFutureDateArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestDefaultInvoiceUserApi extends InvoiceTestSuiteWithEmbeddedDB {

    private UUID accountId;
    private UUID invoiceId;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        final Account account = invoiceUtil.createAccount(callContext);
        accountId = account.getId();
        invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);
    }

    @Test(groups = "slow")
    public void testPostExternalChargeOnNewInvoice() throws Exception {
        // Initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, null, "description", clock.getUTCToday(), clock.getUTCToday(), externalChargeAmount, accountCurrency, null);
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
        verifyExternalChargeOnNewInvoice(accountBalance, null, externalChargeAmount, externalChargeInvoiceItem);

        assertEquals(externalChargeInvoiceItem.getDescription(), "description");
    }

    @Test(groups = "slow")
    public void testPostExternalChargeForBundleOnNewInvoice() throws Exception {
        // Initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, bundleId, UUID.randomUUID().toString(), clock.getUTCToday(), clock.getUTCToday(),externalChargeAmount, accountCurrency, null);
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
        verifyExternalChargeOnNewInvoice(accountBalance, bundleId, externalChargeAmount, externalChargeInvoiceItem);
    }

    private void verifyExternalChargeOnNewInvoice(final BigDecimal initialAccountBalance, @Nullable final UUID bundleId,
                                                  final BigDecimal externalChargeAmount, final InvoiceItem externalChargeInvoiceItem) throws InvoiceApiException {
        Assert.assertNotNull(externalChargeInvoiceItem.getInvoiceId());
        Assert.assertNotEquals(externalChargeInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(externalChargeInvoiceItem.getBundleId(), bundleId);
        Assert.assertEquals(externalChargeInvoiceItem.getInvoiceItemType(), InvoiceItemType.EXTERNAL_CHARGE);
        Assert.assertEquals(externalChargeInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(externalChargeInvoiceItem.getAmount().compareTo(externalChargeAmount), 0);
        Assert.assertEquals(externalChargeInvoiceItem.getCurrency(), accountCurrency);
        Assert.assertNull(externalChargeInvoiceItem.getLinkedItemId());

        // Verify the adjusted invoice balance
        final BigDecimal adjustedInvoiceBalance = invoiceUserApi.getInvoice(externalChargeInvoiceItem.getInvoiceId(), callContext).getBalance();
        Assert.assertEquals(adjustedInvoiceBalance.compareTo(externalChargeAmount), 0);

        // Verify the adjusted account balance
        final BigDecimal adjustedAccountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(adjustedAccountBalance, initialAccountBalance.add(externalChargeAmount));
    }


    @Test(groups = "slow")
    public void testOriginalAmountCharged() throws Exception {

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, null, UUID.randomUUID().toString(), clock.getUTCToday(), null, externalChargeAmount, accountCurrency, null);
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);

        final Invoice newInvoice = invoiceUserApi.getInvoice(externalChargeInvoiceItem.getInvoiceId(), callContext);
        final BigDecimal newAmountCharged = newInvoice.getChargedAmount();
        Assert.assertEquals(newInvoice.getOriginalChargedAmount().compareTo(externalChargeAmount), 0);
        Assert.assertEquals(newAmountCharged.compareTo(externalChargeAmount), 0);
    }

    @Test(groups = "slow")
    public void testPostExternalChargeForBundle() throws Exception {

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, bundleId, UUID.randomUUID().toString(), clock.getUTCToday(), null, externalChargeAmount, accountCurrency, null);
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
        Assert.assertEquals(externalChargeInvoiceItem.getBundleId(), bundleId);
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class, expectedExceptionsMessageRegExp = ".*it is already in COMMITTED status")
    public void testAdjustCommittedInvoice() throws Exception {
        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for the full amount
        final InvoiceItem creditInvoiceItem = invoiceUserApi.insertCreditForInvoice(accountId, invoiceId, invoiceBalance,
                                                                                    clock.getUTCToday(), accountCurrency, "some description", null, null, callContext);
        Assert.assertEquals(creditInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(creditInvoiceItem.getInvoiceItemType(), InvoiceItemType.CREDIT_ADJ);
        Assert.assertEquals(creditInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(creditInvoiceItem.getAmount().compareTo(invoiceBalance.negate()), 0);
        Assert.assertEquals(creditInvoiceItem.getCurrency(), accountCurrency);
        Assert.assertEquals(creditInvoiceItem.getDescription(), "some description");
        Assert.assertNull(creditInvoiceItem.getLinkedItemId());

        // Verify the adjusted invoice balance
        final BigDecimal adjustedInvoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(adjustedInvoiceBalance.compareTo(BigDecimal.ZERO), 0);

        // Verify the adjusted account balance
        final BigDecimal adjustedAccountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(adjustedAccountBalance, adjustedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testCantAdjustInvoiceWithNegativeAmount() throws Exception {
        try {
            invoiceUserApi.insertCreditForInvoice(accountId, invoiceId, BigDecimal.TEN.negate(), clock.getUTCToday(), accountCurrency,
                                                  null, null, null, callContext);
            Assert.fail("Should not have been able to adjust an invoice with a negative amount");
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CREDIT_AMOUNT_INVALID.getCode());
        }
    }

    @Test(groups = "slow")
    public void testAdjustFullInvoiceItem() throws Exception {
        final InvoiceItem invoiceItem = invoiceUserApi.getInvoice(invoiceId, callContext).getInvoiceItems().get(0);
        // Verify we picked a non zero item
        Assert.assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for the full amount
        final InvoiceItem adjInvoiceItem = invoiceUserApi.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItem.getId(),
                                                                                      clock.getUTCToday(), null, null, null, callContext);
        Assert.assertEquals(adjInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(adjInvoiceItem.getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        Assert.assertEquals(adjInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(adjInvoiceItem.getAmount(), invoiceItem.getAmount().negate());
        Assert.assertEquals(adjInvoiceItem.getCurrency(), accountCurrency);
        Assert.assertEquals(adjInvoiceItem.getLinkedItemId(), invoiceItem.getId());

        // Verify the adjusted invoice balance
        final BigDecimal adjustedInvoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        verifyAdjustedInvoiceBalance(invoiceBalance, invoiceItem.getAmount(), invoiceItem.getCurrency(), adjustedInvoiceBalance);

        // Verify the adjusted account balance
        final BigDecimal adjustedAccountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(adjustedAccountBalance, adjustedInvoiceBalance);

        // Verify idempotency
        Assert.assertNull(invoiceUserApi.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItem.getId(), clock.getUTCToday(), null, null, null, callContext));
    }

    @Test(groups = "slow")
    public void testAdjustPartialRecurringInvoiceItem() throws Exception {
        testAdjustPartialInvoiceItem(true);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/pull/831")
    public void testAdjustPartialFixedInvoiceItem() throws Exception {
        testAdjustPartialInvoiceItem(false);
    }


    @Test(groups = "slow")
    public void testAddTaxItems() throws Exception {
        final BigDecimal taxItemAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem taxItem = new TaxInvoiceItem(null, accountId, bundleId, UUID.randomUUID().toString(), clock.getUTCToday(), taxItemAmount, accountCurrency);

        final List<InvoiceItem> resultTaxInvoiceItems = invoiceUserApi.insertTaxItems(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(taxItem), true, null, callContext);
        Assert.assertEquals(resultTaxInvoiceItems.size(), 1);
        Assert.assertEquals(resultTaxInvoiceItems.get(0).getAmount().compareTo(taxItemAmount), 0);
        Assert.assertEquals(resultTaxInvoiceItems.get(0).getBundleId(), bundleId);
    }


    private void testAdjustPartialInvoiceItem(final boolean recurring) throws Exception {
        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final BigDecimal fixedPrice = recurring ? null : BigDecimal.ONE;
        final BigDecimal recurringPrice = !recurring ? null : BigDecimal.ONE;
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, fixedPrice, recurringPrice, null, callContext);

        final InvoiceItem invoiceItem = invoiceUserApi.getInvoice(invoiceId, callContext).getInvoiceItems().get(0);
        // Verify we picked a non zero item
        Assert.assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice with most of the amount (-1 cent)
        final BigDecimal adjAmount = invoiceItem.getAmount().subtract(new BigDecimal("0.01"));
        final InvoiceItem adjInvoiceItem = invoiceUserApi.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItem.getId(),
                                                                                      clock.getUTCToday(), adjAmount, accountCurrency,
                                                                                      null, null, null, callContext);
        Assert.assertEquals(adjInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(adjInvoiceItem.getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        Assert.assertEquals(adjInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(adjInvoiceItem.getAmount(), adjAmount.negate());
        Assert.assertEquals(adjInvoiceItem.getCurrency(), accountCurrency);
        Assert.assertEquals(adjInvoiceItem.getLinkedItemId(), invoiceItem.getId());

        // Verify the adjusted invoice balance
        final BigDecimal adjustedInvoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        verifyAdjustedInvoiceBalance(invoiceBalance, adjAmount, accountCurrency, adjustedInvoiceBalance);

        // Verify the adjusted account balance
        final BigDecimal adjustedAccountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(adjustedAccountBalance, adjustedInvoiceBalance);

        // Verify future invoice generation
        invoiceUtil.generateInvoice(account.getId(), null, new DryRunFutureDateArguments(), internalCallContext);
        // Invoice may or may not be generated, but there is no exception
    }

    @Test(groups = "slow")
    public void testCantAdjustInvoiceItemWithNegativeAmount() throws Exception {
        final InvoiceItem invoiceItem = invoiceUserApi.getInvoice(invoiceId, callContext).getInvoiceItems().get(0);

        try {
            invoiceUserApi.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItem.getId(), clock.getUTCToday(),
                                                       BigDecimal.TEN.negate(), accountCurrency, null, null, null, callContext);
            Assert.fail("Should not have been able to adjust an item with a negative amount");
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_ITEM_ADJUSTMENT_AMOUNT_SHOULD_BE_POSITIVE.getCode());
        }
    }

    private void verifyAdjustedInvoiceBalance(final BigDecimal invoiceBalance, final BigDecimal adjAmount, final Currency currency, final BigDecimal adjustedInvoiceBalance) {
        final BigDecimal expectedBalance = KillBillMoney.of(invoiceBalance.add(adjAmount.negate()), currency);
        Assert.assertEquals(adjustedInvoiceBalance.compareTo(expectedBalance), 0);
    }

    @Test(groups = "slow")
    public void testAddRemoveWrittenOffTag() throws InvoiceApiException, TagApiException {

        final Invoice originalInvoice = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(originalInvoice.getBalance().compareTo(BigDecimal.ZERO), 1);

        invoiceUserApi.tagInvoiceAsWrittenOff(invoiceId, callContext);

        List<Tag> tags = tagUserApi.getTagsForObject(invoiceId, ObjectType.INVOICE, false, callContext);
        assertEquals(tags.size(), 1);
        assertEquals(tags.get(0).getTagDefinitionId(), ControlTagType.WRITTEN_OFF.getId());

        final Invoice invoiceWithTag = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoiceWithTag.getBalance().compareTo(BigDecimal.ZERO), 0);


        invoiceUserApi.tagInvoiceAsNotWrittenOff(invoiceId, callContext);
        tags = tagUserApi.getTagsForObject(invoiceId, ObjectType.INVOICE, false, callContext);
        assertEquals(tags.size(), 0);

        final Invoice invoiceAfterTagRemoval = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoiceAfterTagRemoval.getBalance().compareTo(BigDecimal.ZERO), 1);
    }

    @Test(groups = "slow")
    public void testCommitInvoice() throws Exception {
        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for the full amount
        final BigDecimal creditAmount = BigDecimal.TEN;
        final InvoiceItem creditInvoiceItem = invoiceUserApi.insertCreditForInvoice(accountId, null, creditAmount,
                                                                                    clock.getUTCToday(), accountCurrency, null, null, null, callContext);

        final UUID invoiceId = creditInvoiceItem.getInvoiceId();
        Invoice creditInvoice = invoiceUserApi.getInvoice(invoiceId, callContext);
        Assert.assertEquals(creditInvoice.getStatus(), InvoiceStatus.DRAFT);
        Assert.assertEquals(creditInvoiceItem.getInvoiceId(), creditInvoice.getId());

        // Verify DRAFT invoice is not taken into consideration when computing accountBalance
        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance2, accountBalance);

        // move invoice from DRAFT to COMMITTED
        invoiceUserApi.commitInvoice(creditInvoice.getId(), callContext);
        creditInvoice = invoiceUserApi.getInvoice(invoiceId, callContext);
        Assert.assertEquals(creditInvoice.getStatus(), InvoiceStatus.COMMITTED);

        try {
            final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(invoiceId, accountId, null, "Initial external charge", clock.getUTCToday(), null, new BigDecimal("12.33"), accountCurrency, null);
            invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.of(externalCharge), true, null, callContext);
            Assert.fail("Should fail to add external charge on already committed invoice");
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_ALREADY_COMMITTED.getCode());
        }

        try {
            invoiceUserApi.insertCreditForInvoice(accountId, invoiceId, creditAmount,
                                                  clock.getUTCToday(), accountCurrency, null, null, null, callContext);
            Assert.fail("Should fail to add credit on already committed invoice");
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_ALREADY_COMMITTED.getCode());
        }
    }

    @Test(groups = "slow")
    public void testVoidInvoice() throws Exception {
       // try to void invoice
        invoiceUserApi.voidInvoice(invoiceId, callContext);

        final Invoice invoice = invoiceUserApi.getInvoice(invoiceId, callContext);
        Assert.assertEquals(invoice.getStatus(), InvoiceStatus.VOID);
    }

    @Test(groups = "slow")
    public void testVoidInvoiceThatIsPaid() throws Exception {
        InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // create payment
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoiceId, new DateTime(), invoiceBalance, Currency.USD, Currency.USD, null, true);
        invoiceUtil.createPayment(payment, context);

        // try to void invoice, it should fail
        try {
            invoiceUserApi.voidInvoice(invoiceId, callContext);
            Assert.fail("Should fail to void invoice that is already paid");
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAN_NOT_VOID_INVOICE_THAT_IS_PAID.getCode());
        }

    }
}
