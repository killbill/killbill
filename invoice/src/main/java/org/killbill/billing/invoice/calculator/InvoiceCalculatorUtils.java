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

package org.killbill.billing.invoice.calculator;

import java.math.BigDecimal;
import java.util.Iterator;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.util.currency.KillBillMoney;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public abstract class InvoiceCalculatorUtils {

    public static boolean isInvoiceAdjustmentItem(final InvoiceItem invoiceItemToCheck, final Iterable<InvoiceItem> invoiceItems) {
        // Invoice level credit, i.e. credit adj, but NOT on its on own invoice
    	
    	return InvoiceItemType.CREDIT_ADJ.equals(invoiceItemToCheck.getInvoiceItemType())  &&
                !isCreditInvoice(invoiceItems);
    }

    private static boolean isCreditInvoice(final Iterable<InvoiceItem> invoiceItems) { 

        if (Iterables.size(invoiceItems) != 2) { 
            return false;
        }

        final Iterator<InvoiceItem> itr = invoiceItems.iterator();

        final InvoiceItem item1 = itr.next();
        final InvoiceItem item2 = itr.next();

        if (!InvoiceItemType.CREDIT_ADJ.equals(item1.getInvoiceItemType()) && !InvoiceItemType.CREDIT_ADJ.equals(item2.getInvoiceItemType())) {
            return false;
        }
        
        return ((InvoiceItemType.CREDIT_ADJ.equals(item1.getInvoiceItemType()) && isCbaAdjItemInCreditInvoice(item1, item2)) || 
        		(InvoiceItemType.CREDIT_ADJ.equals(item2.getInvoiceItemType()) && isCbaAdjItemInCreditInvoice(item2, item1)));

    }

    private static boolean isCbaAdjItemInCreditInvoice(final InvoiceItem creditAdjItem, final InvoiceItem itemToCheck) { 
    	return (InvoiceItemType.CBA_ADJ.equals(itemToCheck.getInvoiceItemType()) && 
        		itemToCheck.getInvoiceId().equals(creditAdjItem.getInvoiceId()) && 
        		itemToCheck.getAmount().compareTo(creditAdjItem.getAmount().negate()) == 0);
    }

    // Item adjustments
    public static boolean isInvoiceItemAdjustmentItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType()) || InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Account credits, gained or consumed
    public static boolean isAccountCreditItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Item from Parent Invoice
    public static boolean isParentSummaryItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.PARENT_SUMMARY.equals(invoiceItem.getInvoiceItemType());
    }

    // Regular line item (charges)
    public static boolean isCharge(final InvoiceItem invoiceItem) {
        return InvoiceItemType.TAX.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.EXTERNAL_CHARGE.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.FIXED.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.USAGE.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.RECURRING.equals(invoiceItem.getInvoiceItemType());
    }

    public static BigDecimal computeRawInvoiceBalance(final Currency currency,
                                                      @Nullable final Iterable<InvoiceItem> invoiceItems,
                                                      @Nullable final Iterable<InvoicePayment> invoicePayments) {

        final BigDecimal amountPaid = computeInvoiceAmountPaid(currency, invoicePayments)
                .add(computeInvoiceAmountRefunded(currency, invoicePayments));

        final BigDecimal chargedAmount = computeInvoiceAmountCharged(currency, invoiceItems)
                .add(computeInvoiceAmountCredited(currency, invoiceItems))
                .add(computeInvoiceAmountAdjustedForAccountCredit(currency, invoiceItems));

        final BigDecimal invoiceBalance = chargedAmount.add(amountPaid.negate());

        return KillBillMoney.of(invoiceBalance, currency);
    }

    public static BigDecimal computeChildInvoiceAmount(final Currency currency,
                                                       @Nullable final Iterable<InvoiceItem> invoiceItems) {
        if (invoiceItems == null) {
            return BigDecimal.ZERO;
        }

        final Iterable<InvoiceItem> chargeItems = Iterables.filter(invoiceItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return isCharge(input);
            }
        });

        if (Iterables.isEmpty(chargeItems)) {
            // return only credit amount to be subtracted to parent item amount
            return computeInvoiceAmountCredited(currency, invoiceItems).negate();
        }

        final BigDecimal chargedAmount = computeInvoiceAmountCharged(currency, invoiceItems)
                .add(computeInvoiceAmountCredited(currency, invoiceItems))
                .add(computeInvoiceAmountAdjustedForAccountCredit(currency, invoiceItems));
        return KillBillMoney.of(chargedAmount, currency);
    }

    // Snowflake for the CREDIT_ADJ on its own invoice
    private static BigDecimal computeInvoiceAmountAdjustedForAccountCredit(final Currency currency,
                                                                           final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountAdjusted = BigDecimal.ZERO;
        if (invoiceItems == null || !invoiceItems.iterator().hasNext()) {
            return KillBillMoney.of(amountAdjusted, currency);
        }

        if (isCreditInvoice(invoiceItems)) { //if the invoice corresponds to a CREDIT, include its amount

            Iterator<InvoiceItem> itr = invoiceItems.iterator();

            InvoiceItem item1 = itr.next();
            InvoiceItem item2 = itr.next();

            if (InvoiceItemType.CREDIT_ADJ.equals(item1.getInvoiceItemType())) {
                amountAdjusted = amountAdjusted.add(item1.getAmount());
            } else  { // since the isCreditInvoice is already checked, this implies that item2 is of type CREDIT_ADJ
                amountAdjusted = amountAdjusted.add(item2.getAmount());
            }
        }
        return KillBillMoney.of(amountAdjusted, currency);
    }

    public static BigDecimal computeInvoiceAmountCharged(final Currency currency, @Nullable final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountCharged = BigDecimal.ZERO;
        if (invoiceItems == null || !invoiceItems.iterator().hasNext()) {
            return KillBillMoney.of(amountCharged, currency);
        }

        for (final InvoiceItem invoiceItem : invoiceItems) {

            if (isCharge(invoiceItem) ||
                isInvoiceAdjustmentItem(invoiceItem, invoiceItems) ||
                isInvoiceItemAdjustmentItem(invoiceItem) ||
                isParentSummaryItem(invoiceItem)) {
                amountCharged = amountCharged.add(invoiceItem.getAmount());
            }
        }

        return KillBillMoney.of(amountCharged, currency);
    }

    public static BigDecimal computeInvoiceOriginalAmountCharged(final DateTime invoiceCreatedDate, final Currency currency, @Nullable final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountCharged = BigDecimal.ZERO;
        if (invoiceItems == null || !invoiceItems.iterator().hasNext()) {
            return KillBillMoney.of(amountCharged, currency);
        }

        for (final InvoiceItem invoiceItem : invoiceItems) {
            if (isCharge(invoiceItem) &&
                (invoiceItem.getCreatedDate() != null && invoiceItem.getCreatedDate().equals(invoiceCreatedDate))) {
                amountCharged = amountCharged.add(invoiceItem.getAmount());
            }
        }

        return KillBillMoney.of(amountCharged, currency);
    }

    public static BigDecimal computeInvoiceAmountCredited(final Currency currency, @Nullable final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountCredited = BigDecimal.ZERO;
        if (invoiceItems == null || !invoiceItems.iterator().hasNext()) {
            return KillBillMoney.of(amountCredited, currency);
        }

        for (final InvoiceItem invoiceItem : invoiceItems) {
            if (isAccountCreditItem(invoiceItem)) {
                amountCredited = amountCredited.add(invoiceItem.getAmount());
            }
        }

        return KillBillMoney.of(amountCredited, currency);
    }

    public static BigDecimal computeInvoiceAmountPaid(final Currency currency, @Nullable final Iterable<InvoicePayment> invoicePayments) {
        BigDecimal amountPaid = BigDecimal.ZERO;
        if (invoicePayments == null || !invoicePayments.iterator().hasNext()) {
            return KillBillMoney.of(amountPaid, currency);
        }

        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (!invoicePayment.isSuccess()) {
                continue;
            }
            if (InvoicePaymentType.ATTEMPT.equals(invoicePayment.getType())) {
                amountPaid = amountPaid.add(invoicePayment.getAmount());
            }
        }

        return KillBillMoney.of(amountPaid, currency);
    }

    public static BigDecimal computeInvoiceAmountRefunded(final Currency currency, @Nullable final Iterable<InvoicePayment> invoicePayments) {
        BigDecimal amountRefunded = BigDecimal.ZERO;
        if (invoicePayments == null || !invoicePayments.iterator().hasNext()) {
            return KillBillMoney.of(amountRefunded, currency);
        }

        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (invoicePayment.isSuccess() == null || !invoicePayment.isSuccess()) {
                continue;
            }
            if (InvoicePaymentType.REFUND.equals(invoicePayment.getType()) ||
                InvoicePaymentType.CHARGED_BACK.equals(invoicePayment.getType())) {
                amountRefunded = amountRefunded.add(invoicePayment.getAmount());
            }
        }

        return KillBillMoney.of(amountRefunded, currency);
    }
}
