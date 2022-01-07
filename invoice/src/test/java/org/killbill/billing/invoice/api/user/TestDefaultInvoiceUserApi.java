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
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static org.testng.Assert.assertEquals;

public class TestDefaultInvoiceUserApi extends InvoiceTestSuiteWithEmbeddedDB {

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
    }

    @Test(groups = "slow")
    public void testPostExternalChargeOnNewInvoice() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        // Initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, null, "description", clock.getUTCToday(), clock.getUTCToday(), externalChargeAmount, accountCurrency, null);
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
        verifyExternalChargeOnNewInvoice(accountId, invoiceId, accountBalance, null, externalChargeAmount, externalChargeInvoiceItem);

        assertEquals(externalChargeInvoiceItem.getDescription(), "description");
    }

    @Test(groups = "slow")
    public void testPostExternalChargeForBundleOnNewInvoice() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        // Initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, bundleId, UUID.randomUUID().toString(), clock.getUTCToday(), clock.getUTCToday(), externalChargeAmount, accountCurrency, null);
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
        verifyExternalChargeOnNewInvoice(accountId, invoiceId, accountBalance, bundleId, externalChargeAmount, externalChargeInvoiceItem);
    }

    private void verifyExternalChargeOnNewInvoice(final UUID accountId, final UUID invoiceId, final BigDecimal initialAccountBalance, @Nullable final UUID bundleId,
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

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

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

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, bundleId, UUID.randomUUID().toString(), clock.getUTCToday(), null, externalChargeAmount, accountCurrency, null);
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
        Assert.assertEquals(externalChargeInvoiceItem.getBundleId(), bundleId);
    }

    @Test(groups = "slow", expectedExceptions = InvoiceApiException.class, expectedExceptionsMessageRegExp = ".*it is already in COMMITTED status")
    public void testAdjustCommittedInvoice() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for the full amount
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(invoiceId, accountId, clock.getUTCToday(), "some description", invoiceBalance, accountCurrency, null);
        final List<InvoiceItem> creditInvoiceItems = invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(inputCredit), false, null, callContext);
        Assert.assertEquals(creditInvoiceItems.size(), 1);

        final InvoiceItem creditInvoiceItem = creditInvoiceItems.get(0);
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

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        try {

            final InvoiceItem inputCredit = new CreditAdjInvoiceItem(invoiceId, accountId, clock.getUTCToday(), "some description", BigDecimal.TEN.negate(), accountCurrency, null);
            invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(inputCredit), false, null, callContext);
            Assert.fail("Should not have been able to adjust an invoice with a negative amount");
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CREDIT_AMOUNT_INVALID.getCode());
        }
    }

    @Test(groups = "slow")
    public void testAdjustFullInvoiceItem() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

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

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        final BigDecimal taxItemAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem taxItem = new TaxInvoiceItem(null, accountId, bundleId, UUID.randomUUID().toString(), clock.getUTCToday(), taxItemAmount, accountCurrency);

        final List<InvoiceItem> resultTaxInvoiceItems = invoiceUserApi.insertTaxItems(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(taxItem), true, ImmutableList.of(), callContext);
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

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

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
    public void testAddRemoveWrittenOffTag() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

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

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for the full amount
        final BigDecimal creditAmount = BigDecimal.TEN;

        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, accountId, clock.getUTCToday(), "some description", creditAmount, accountCurrency, null);
        final List<InvoiceItem> creditInvoiceItems = invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(inputCredit), false, null, callContext);

        Assert.assertEquals(creditInvoiceItems.size(), 1);
        final InvoiceItem creditInvoiceItem = creditInvoiceItems.get(0);

        final UUID invoiceId2 = creditInvoiceItem.getInvoiceId();
        Invoice creditInvoice = invoiceUserApi.getInvoice(invoiceId2, callContext);
        Assert.assertEquals(creditInvoice.getStatus(), InvoiceStatus.DRAFT);
        Assert.assertEquals(creditInvoiceItem.getInvoiceId(), creditInvoice.getId());

        // Verify DRAFT invoice is not taken into consideration when computing accountBalance
        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance2, accountBalance);

        // move invoice from DRAFT to COMMITTED
        invoiceUserApi.commitInvoice(creditInvoice.getId(), callContext);
        creditInvoice = invoiceUserApi.getInvoice(invoiceId2, callContext);
        Assert.assertEquals(creditInvoice.getStatus(), InvoiceStatus.COMMITTED);

        try {
            final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(invoiceId2, accountId, null, "Initial external charge", clock.getUTCToday(), null, new BigDecimal("12.33"), accountCurrency, null);
            invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.of(externalCharge), true, null, callContext);
            Assert.fail("Should fail to add external charge on already committed invoice");
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_ALREADY_COMMITTED.getCode());
        }

        try {
            final InvoiceItem inputCreditForInvoice = new CreditAdjInvoiceItem(invoiceId2, accountId, clock.getUTCToday(), "some description", creditAmount, accountCurrency, null);
            invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(inputCreditForInvoice), false, null, callContext);
            Assert.fail("Should fail to add credit on already committed invoice");
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_ALREADY_COMMITTED.getCode());
        }
    }

    @Test(groups = "slow")
    public void testVoidInvoice() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

        // Void invoice
        invoiceUserApi.voidInvoice(invoiceId, callContext);
        final Invoice invoice = invoiceUserApi.getInvoice(invoiceId, callContext);
        Assert.assertEquals(invoice.getStatus(), InvoiceStatus.VOID);

        // Check we cannot add items on a VOIDed invoice
        try {
            final InvoiceItem creditItem = new CreditAdjInvoiceItem(invoiceId, accountId, clock.getUTCToday(), "something", BigDecimal.TEN, accountCurrency, null);
            invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(creditItem), true, null, callContext);
            Assert.fail("Should not allow to add items to a VOIDed invoice");
        } catch (final Exception ignore) {
            // No check because of  https://github.com/killbill/killbill/issues/1501
        }

        try {
            final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(invoiceId, accountId, null, "description", clock.getUTCToday(), clock.getUTCToday(), BigDecimal.TEN, accountCurrency, null);
            invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
            Assert.fail("Should not allow to add items to a VOIDed invoice");
        } catch (final IllegalStateException ignore) {
            // No check because of  https://github.com/killbill/killbill/issues/1501
        }

        try {
            final InvoiceItem originalItem = invoice.getInvoiceItems().get(0);
            invoiceUserApi.insertInvoiceItemAdjustment(accountId, invoiceId, originalItem.getId(), clock.getUTCToday(), null, null, null, callContext);
            Assert.fail("Should not allow to add items to a VOIDed invoice");
        } catch (final Exception ignore) {
            // No check because of  https://github.com/killbill/killbill/issues/1501
        }
    }

    @Test(groups = "slow")
    public void testVoidInvoiceThatIsPaid() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();
        final UUID invoiceId = invoiceUtil.generateRegularInvoice(account, null, callContext);

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

    @Test(groups = "slow")
    public void testVoidInvoiceWithUsedCredits() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();

        final InvoiceItem creditItem = new CreditAdjInvoiceItem(null, accountId, clock.getUTCToday(), "something", new BigDecimal("200.00"), accountCurrency, null);
        invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(creditItem), true, null, callContext);

        final InvoiceItem externalCharge1 = new ExternalChargeInvoiceItem(null, accountId, null, "description", clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("100.00"), accountCurrency, null);
        final List<InvoiceItem> items1 = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge1), true, null, callContext);
        assertEquals(items1.size(), 1);
        final Invoice invoice1 = invoiceUserApi.getInvoice(items1.get(0).getInvoiceId(), callContext);

        final InvoiceItem externalCharge2 = new ExternalChargeInvoiceItem(null, accountId, null, "description", clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("50.00"), accountCurrency, null);
        final List<InvoiceItem> items2 = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge2), true, null, callContext);
        assertEquals(items2.size(), 1);
        final Invoice invoice2 = invoiceUserApi.getInvoice(items2.get(0).getInvoiceId(), callContext);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        final BigDecimal accountCBA = invoiceUserApi.getAccountCBA(accountId, callContext);
        Assert.assertEquals(accountBalance.compareTo(new BigDecimal("-50")), 0);
        Assert.assertEquals(accountCBA.compareTo(new BigDecimal("50")), 0);

        invoiceUserApi.voidInvoice(invoice1.getId(), callContext);

        // We verify that credit *used* on invoice1 is reclaimed, i.e the system ignores it as the invoice was voided.
        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(accountId, callContext);
        final BigDecimal accountCBA2 = invoiceUserApi.getAccountCBA(accountId, callContext);
        Assert.assertEquals(accountBalance2.compareTo(new BigDecimal("-150")), 0);
        Assert.assertEquals(accountCBA2.compareTo(new BigDecimal("150")), 0);
    }

    @Test(groups = "slow")
    public void testDeleteUserGeneratedCredit() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();

        // Invoice created in DRAFT state so we can add credit items on top of it
        final InvoiceItem externalCharge1 = new ExternalChargeInvoiceItem(null, accountId, null, "description", clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("100.00"), accountCurrency, null);
        final List<InvoiceItem> items1 = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge1), false, null, callContext);
        assertEquals(items1.size(), 1);
        final Invoice invoice1 = invoiceUserApi.getInvoice(items1.get(0).getInvoiceId(), callContext);

        final InvoiceItem creditItemInput = new CreditAdjInvoiceItem(invoice1.getId(), accountId, clock.getUTCToday(), "something", new BigDecimal("200.00"), accountCurrency, null);
        final List<InvoiceItem> itemCredits = invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(creditItemInput), true, null, callContext);
        final InvoiceItem itemCredit = itemCredits.get(0);

        // No credit as invoice is still in DRAFT
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        final BigDecimal accountCBA = invoiceUserApi.getAccountCBA(accountId, callContext);
        Assert.assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(accountCBA.compareTo(BigDecimal.ZERO), 0);

        // Create new invoice (COMMITTED)
        final InvoiceItem externalCharge2 = new ExternalChargeInvoiceItem(null, accountId, null, "description", clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("50.00"), accountCurrency, null);
        final List<InvoiceItem> items2 = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge2), true, null, callContext);
        assertEquals(items2.size(), 1);
        final Invoice invoice2 = invoiceUserApi.getInvoice(items2.get(0).getInvoiceId(), callContext);

        // Verify balance and credit only reflect invoice2
        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(accountId, callContext);
        final BigDecimal accountCBA2 = invoiceUserApi.getAccountCBA(accountId, callContext);
        Assert.assertEquals(accountBalance2.compareTo(new BigDecimal("50")), 0);
        Assert.assertEquals(accountCBA2.compareTo(BigDecimal.ZERO), 0);

        // Commit invoice 1
        invoiceUserApi.commitInvoice(invoice1.getId(), callContext);

        final BigDecimal accountBalance3 = invoiceUserApi.getAccountBalance(accountId, callContext);
        final BigDecimal accountCBA3 = invoiceUserApi.getAccountCBA(accountId, callContext);
        Assert.assertEquals(accountBalance3.compareTo(new BigDecimal("-50")), 0);
        Assert.assertEquals(accountCBA3.compareTo(new BigDecimal("50")), 0);

        // Void invoice1 where we initially generated the credit -> fails because there was credit generated on it
        try {
            invoiceUserApi.voidInvoice(invoice1.getId(), callContext);
            Assert.fail("Should not allow to void invoice with credit");
        } catch (final Exception ignore) {
            // No check because of  https://github.com/killbill/killbill/issues/1501
        }

        Invoice latestInvoice1 = invoiceUserApi.getInvoice(items1.get(0).getInvoiceId(), callContext);
        final InvoiceItem creditGenItem = Iterables.tryFind(latestInvoice1.getInvoiceItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem invoiceItem) {
                return InvoiceItemType.CBA_ADJ == invoiceItem.getInvoiceItemType() && invoiceItem.getAmount().compareTo(BigDecimal.ZERO) > 0;
            }
        }).get();

        // Delete the credit generation
        invoiceUserApi.deleteCBA(accountId, invoice1.getId(), creditGenItem.getId(), callContext);

        latestInvoice1 = invoiceUserApi.getInvoice(items1.get(0).getInvoiceId(), callContext);
        Assert.assertEquals(latestInvoice1.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(latestInvoice1.getInvoiceItems().size(), 3);

        Assert.assertNotNull(Iterables.tryFind(latestInvoice1.getInvoiceItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem invoiceItem) {
                return InvoiceItemType.CBA_ADJ == invoiceItem.getInvoiceItemType() && invoiceItem.getAmount().compareTo(BigDecimal.ZERO) == 0;
            }
        }).orNull());
        Assert.assertNotNull(Iterables.tryFind(latestInvoice1.getInvoiceItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem invoiceItem) {
                return InvoiceItemType.CREDIT_ADJ == invoiceItem.getInvoiceItemType() &&
                       // Original credit amount - CBA gen (we just deleted)
                       invoiceItem.getAmount().compareTo(new BigDecimal("-100.00")) == 0;
            }
        }).orNull());

        final BigDecimal accountBalance4 = invoiceUserApi.getAccountBalance(accountId, callContext);
        final BigDecimal accountCBA4 = invoiceUserApi.getAccountCBA(accountId, callContext);
        Assert.assertEquals(accountBalance4.compareTo(new BigDecimal("50.0")), 0);
        Assert.assertEquals(accountCBA4.compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testCreditOnDraftInvoice() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();

        // Create external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, null, UUID.randomUUID().toString(), clock.getUTCToday(), null, externalChargeAmount, accountCurrency, null);

        // Create invoice in draft status with external charge
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), false, null, callContext).get(0);
        final Invoice externalChargeInvoice = invoiceUserApi.getInvoice(externalChargeInvoiceItem.getInvoiceId(), callContext);
        Assert.assertEquals(externalChargeInvoice.getChargedAmount().compareTo(externalChargeAmount), 0);
        Assert.assertEquals(externalChargeInvoice.getBalance().compareTo(BigDecimal.ZERO), 0); // invoice is in DRAFT status, so balance is 0

        //Post a credit on draft invoice
        final BigDecimal creditAmount = new BigDecimal(3);
        final InvoiceItem credit = new CreditAdjInvoiceItem(externalChargeInvoice.getId(), accountId, clock.getUTCToday(), "Adding credit", creditAmount, accountCurrency, null);
        invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(credit), true, null, callContext);

        final Invoice updatedExternalChargeInvoice = invoiceUserApi.getInvoice(externalChargeInvoice.getId(), callContext);
        Assert.assertEquals(updatedExternalChargeInvoice.getChargedAmount().compareTo(new BigDecimal(7)), 0); // credit is adjusted in the amountCharged since invoice is in DRAFT status 
        Assert.assertEquals(updatedExternalChargeInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);// invoice is in DRAFT status, so balance is 0
        
        //commit invoice
        invoiceUserApi.commitInvoice(externalChargeInvoice.getId(), callContext);
        final Invoice committedInvoice = invoiceUserApi.getInvoice(externalChargeInvoice.getId(), callContext);
        Assert.assertEquals(committedInvoice.getChargedAmount().compareTo(new BigDecimal(7)), 0);
        Assert.assertEquals(committedInvoice.getBalance().compareTo(new BigDecimal(7)), 0); //invoice is committed so balance is now 7
    }

    @Test(groups = "slow")
    public void testCreditWithoutDraftInvoice() throws Exception {

        final Account account = invoiceUtil.createAccount(callContext);
        final UUID accountId = account.getId();

        // Create an external charge invoice
        final BigDecimal externalChargeAmount = new BigDecimal(300);
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, accountId, null, UUID.randomUUID().toString(), clock.getUTCToday(), null, externalChargeAmount, accountCurrency, null);
        InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), true, null, callContext).get(0);
        final Invoice externalChargeInvoice = invoiceUserApi.getInvoice(externalChargeInvoiceItem.getInvoiceId(), callContext);
        Assert.assertEquals(externalChargeInvoice.getBalance().compareTo(externalChargeAmount), 0);
        Assert.assertEquals(externalChargeInvoice.getChargedAmount().compareTo(externalChargeAmount), 0);

        // Create a credit invoice
        final InvoiceItem creditItem = new CreditAdjInvoiceItem(null, accountId, clock.getUTCToday(), "something", new BigDecimal("200.00"), accountCurrency, null);
        final InvoiceItem creditInvoiceItem = invoiceUserApi.insertCredits(accountId, clock.getUTCToday(), ImmutableList.of(creditItem), true, null, callContext).get(0);
        final Invoice creditInvoice = invoiceUserApi.getInvoice(creditInvoiceItem.getInvoiceId(), callContext);
        Assert.assertEquals(creditInvoice.getBalance().compareTo(new BigDecimal(0)), 0);
        Assert.assertEquals(creditInvoice.getChargedAmount().compareTo(new BigDecimal(0)), 0);

        // Verify that invoice balance reflects the credit
        final Invoice updatedExternalChargeInvoice = invoiceUserApi.getInvoice(externalChargeInvoiceItem.getInvoiceId(), callContext);
        Assert.assertEquals(updatedExternalChargeInvoice.getBalance().compareTo(new BigDecimal(100)), 0);

        // Verify that amountCharged DOES NOT reflect the credit since invoice is NOT in DRAFT status
        Assert.assertEquals(updatedExternalChargeInvoice.getChargedAmount().compareTo(new BigDecimal(300)), 0);

    }
}
