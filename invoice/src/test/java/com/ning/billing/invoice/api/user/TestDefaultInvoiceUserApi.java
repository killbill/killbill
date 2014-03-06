/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.callcontext.DefaultCallContext;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.util.currency.KillBillMoney;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

import static org.testng.Assert.assertEquals;

public class TestDefaultInvoiceUserApi extends InvoiceTestSuiteWithEmbeddedDB {

    private UUID accountId;
    private UUID invoiceId;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final Account account = invoiceUtil.createAccount(callContext);
        accountId = account.getId();
        invoiceId = invoiceUtil.generateRegularInvoice(account, clock.getUTCNow(), callContext);
    }

    @Test(groups = "slow")
    public void testPostExternalChargeOnNewInvoice() throws Exception {
        // Initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalCharge(accountId, externalChargeAmount, UUID.randomUUID().toString(),
                                                                                          clock.getUTCToday(), accountCurrency, callContext);
        verifyExternalChargeOnNewInvoice(accountBalance, null, externalChargeAmount, externalChargeInvoiceItem);
    }

    @Test(groups = "slow")
    public void testPostExternalChargeForBundleOnNewInvoice() throws Exception {
        // Initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalChargeForBundle(accountId, bundleId, externalChargeAmount,
                                                                                                   UUID.randomUUID().toString(), clock.getUTCToday(),
                                                                                                   accountCurrency, callContext);
        verifyExternalChargeOnNewInvoice(accountBalance, bundleId, externalChargeAmount, externalChargeInvoiceItem);
    }

    private void verifyExternalChargeOnNewInvoice(final BigDecimal initialAccountBalance, @Nullable final UUID bundleId,
                                                  final BigDecimal externalChargeAmount, final InvoiceItem externalChargeInvoiceItem) throws InvoiceApiException {
        Assert.assertNotNull(externalChargeInvoiceItem.getInvoiceId());
        Assert.assertNotEquals(externalChargeInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(externalChargeInvoiceItem.getBundleId(), bundleId);
        Assert.assertEquals(externalChargeInvoiceItem.getInvoiceItemType(), InvoiceItemType.EXTERNAL_CHARGE);
        Assert.assertEquals(externalChargeInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(externalChargeInvoiceItem.getAmount(), externalChargeAmount);
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
    public void testPostExternalChargeOnExistingInvoice() throws Exception {
        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);
        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalChargeForInvoice(accountId, invoiceId,
                                                                                                    externalChargeAmount, UUID.randomUUID().toString(),
                                                                                                    clock.getUTCToday(), accountCurrency, callContext);
        verifyExternalChargeOnExistingInvoice(invoiceBalance, null, externalChargeAmount, externalChargeInvoiceItem);
    }

    @Test(groups = "slow", enabled= false)
    public void testOriginalAmountCharged() throws Exception {

        final Invoice initialInvoice = invoiceUserApi.getInvoice(invoiceId, callContext);
        final BigDecimal originalAmountCharged = initialInvoice.getOriginalChargedAmount();
        final BigDecimal amountCharged = initialInvoice.getChargedAmount();
        Assert.assertEquals(originalAmountCharged.compareTo(amountCharged), 0);

        ((ClockMock) clock).addDays(1);

        // Sleep at least one sec to make sure created_date for the external charge is different than the created date for the invoice itself
        CallContext newCallContextLater = new DefaultCallContext(callContext.getTenantId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getUserToken(), clock);
        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalChargeForInvoice(accountId, invoiceId,
                                                                                                    externalChargeAmount, UUID.randomUUID().toString(),
                                                                                                    clock.getUTCToday(), accountCurrency, newCallContextLater);

        final Invoice newInvoice = invoiceUserApi.getInvoice(invoiceId, callContext);
        final BigDecimal newOriginalAmountCharged = newInvoice.getOriginalChargedAmount();
        final BigDecimal newAmountCharged = newInvoice.getChargedAmount();
        final BigDecimal expectedChargedAmount = newInvoice.getOriginalChargedAmount().add(externalChargeInvoiceItem.getAmount());

        Assert.assertEquals(originalAmountCharged.compareTo(newOriginalAmountCharged), 0);
        Assert.assertEquals(newAmountCharged.compareTo(expectedChargedAmount), 0);
    }

    @Test(groups = "slow")
    public void testPostExternalChargeForBundleOnExistingInvoice() throws Exception {
        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Post an external charge
        final BigDecimal externalChargeAmount = BigDecimal.TEN;
        final UUID bundleId = UUID.randomUUID();
        final InvoiceItem externalChargeInvoiceItem = invoiceUserApi.insertExternalChargeForInvoiceAndBundle(accountId, invoiceId, bundleId,
                                                                                                             externalChargeAmount, UUID.randomUUID().toString(),
                                                                                                             clock.getUTCToday(), accountCurrency, callContext);
        verifyExternalChargeOnExistingInvoice(invoiceBalance, bundleId, externalChargeAmount, externalChargeInvoiceItem);
    }

    private void verifyExternalChargeOnExistingInvoice(final BigDecimal initialInvoiceBalance, @Nullable final UUID bundleId,
                                                       final BigDecimal externalChargeAmount, final InvoiceItem externalChargeInvoiceItem) throws InvoiceApiException {
        Assert.assertEquals(externalChargeInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(externalChargeInvoiceItem.getBundleId(), bundleId);
        Assert.assertEquals(externalChargeInvoiceItem.getInvoiceItemType(), InvoiceItemType.EXTERNAL_CHARGE);
        Assert.assertEquals(externalChargeInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(externalChargeInvoiceItem.getAmount(), externalChargeAmount);
        Assert.assertEquals(externalChargeInvoiceItem.getCurrency(), accountCurrency);
        Assert.assertNull(externalChargeInvoiceItem.getLinkedItemId());

        // Verify the adjusted invoice balance
        final BigDecimal adjustedInvoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(adjustedInvoiceBalance.compareTo(initialInvoiceBalance.add(externalChargeAmount)), 0);

        // Verify the adjusted account balance
        final BigDecimal adjustedAccountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(adjustedAccountBalance, adjustedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testAdjustFullInvoice() throws Exception {
        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for the full amount
        final InvoiceItem creditInvoiceItem = invoiceUserApi.insertCreditForInvoice(accountId, invoiceId, invoiceBalance,
                                                                                    clock.getUTCToday(), accountCurrency, callContext);
        Assert.assertEquals(creditInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(creditInvoiceItem.getInvoiceItemType(), InvoiceItemType.CREDIT_ADJ);
        Assert.assertEquals(creditInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(creditInvoiceItem.getAmount(), invoiceBalance.negate());
        Assert.assertEquals(creditInvoiceItem.getCurrency(), accountCurrency);
        Assert.assertNull(creditInvoiceItem.getLinkedItemId());

        // Verify the adjusted invoice balance
        final BigDecimal adjustedInvoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(adjustedInvoiceBalance.compareTo(BigDecimal.ZERO), 0);

        // Verify the adjusted account balance
        final BigDecimal adjustedAccountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(adjustedAccountBalance, adjustedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testAdjustPartialInvoice() throws Exception {
        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for a fraction of the balance
        final BigDecimal creditAmount = invoiceBalance.divide(BigDecimal.TEN);
        final InvoiceItem creditInvoiceItem = invoiceUserApi.insertCreditForInvoice(accountId, invoiceId, creditAmount,
                                                                                    clock.getUTCToday(), accountCurrency, callContext);
        Assert.assertEquals(creditInvoiceItem.getInvoiceId(), invoiceId);
        Assert.assertEquals(creditInvoiceItem.getInvoiceItemType(), InvoiceItemType.CREDIT_ADJ);
        Assert.assertEquals(creditInvoiceItem.getAccountId(), accountId);
        Assert.assertEquals(creditInvoiceItem.getAmount(), creditAmount.negate());
        Assert.assertEquals(creditInvoiceItem.getCurrency(), accountCurrency);
        Assert.assertNull(creditInvoiceItem.getLinkedItemId());

        // Verify the adjusted invoice balance
        final BigDecimal adjustedInvoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        verifyAdjustedInvoiceBalance(invoiceBalance, creditAmount, accountCurrency, adjustedInvoiceBalance);

        // Verify the adjusted account balance
        final BigDecimal adjustedAccountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(adjustedAccountBalance, adjustedInvoiceBalance);
    }

    @Test(groups = "slow")
    public void testCantAdjustInvoiceWithNegativeAmount() throws Exception {
        try {
            invoiceUserApi.insertCreditForInvoice(accountId, invoiceId, BigDecimal.TEN.negate(), clock.getUTCToday(), accountCurrency, callContext);
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
                                                                                      clock.getUTCToday(), callContext);
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
    }

    @Test(groups = "slow")
    public void testAdjustPartialInvoiceItem() throws Exception {
        final InvoiceItem invoiceItem = invoiceUserApi.getInvoice(invoiceId, callContext).getInvoiceItems().get(0);
        // Verify we picked a non zero item
        Assert.assertEquals(invoiceItem.getAmount().compareTo(BigDecimal.ZERO), 1);

        // Verify the initial invoice balance
        final BigDecimal invoiceBalance = invoiceUserApi.getInvoice(invoiceId, callContext).getBalance();
        Assert.assertEquals(invoiceBalance.compareTo(BigDecimal.ZERO), 1);

        // Verify the initial account balance
        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(accountId, callContext);
        Assert.assertEquals(accountBalance, invoiceBalance);

        // Adjust the invoice for a fraction of the balance
        final BigDecimal adjAmount = invoiceItem.getAmount().divide(BigDecimal.TEN);
        final InvoiceItem adjInvoiceItem = invoiceUserApi.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItem.getId(),
                                                                                      clock.getUTCToday(), adjAmount, accountCurrency,
                                                                                      callContext);
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
    }

    @Test(groups = "slow")
    public void testCantAdjustInvoiceItemWithNegativeAmount() throws Exception {
        final InvoiceItem invoiceItem = invoiceUserApi.getInvoice(invoiceId, callContext).getInvoiceItems().get(0);

        try {
            invoiceUserApi.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItem.getId(), clock.getUTCToday(),
                                                       BigDecimal.TEN.negate(), accountCurrency, callContext);
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
        invoiceUserApi.tagInvoiceAsWrittenOff(invoiceId, callContext);

        List<Tag> tags = tagUserApi.getTagsForObject(invoiceId, ObjectType.INVOICE, false, callContext);
        assertEquals(tags.size(), 1);
        assertEquals(tags.get(0).getTagDefinitionId(), ControlTagType.WRITTEN_OFF.getId());

        invoiceUserApi.tagInvoiceAsNotWrittenOff(invoiceId, callContext);
        tags = tagUserApi.getTagsForObject(invoiceId, ObjectType.INVOICE, false, callContext);
        assertEquals(tags.size(), 0);
    }
}
