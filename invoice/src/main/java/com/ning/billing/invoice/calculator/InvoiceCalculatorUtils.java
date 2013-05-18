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

package com.ning.billing.invoice.calculator;

import java.math.BigDecimal;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentType;
import com.ning.billing.invoice.model.InvoicingConfiguration;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public abstract class InvoiceCalculatorUtils {

    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    private static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMode();

    // Invoice adjustments
    public static boolean isInvoiceAdjustmentItem(final InvoiceItem invoiceItem, final Iterable<InvoiceItem> otherInvoiceItems) {
        // Either REFUND_ADJ
        return InvoiceItemType.REFUND_ADJ.equals(invoiceItem.getInvoiceItemType()) ||
               // Or invoice level credit, i.e. credit adj, but NOT on its on own invoice
               (InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                !(Iterables.size(otherInvoiceItems) == 1 &&
                  InvoiceItemType.CBA_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                  otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                  otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0));
    }

    // Item adjustments
    public static boolean isInvoiceItemAdjustmentItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType()) || InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Account credits, gained or consumed
    public static boolean isAccountCreditItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Regular line item (charges)
    public static boolean isCharge(final InvoiceItem invoiceItem) {
        return InvoiceItemType.EXTERNAL_CHARGE.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.FIXED.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.RECURRING.equals(invoiceItem.getInvoiceItemType());
    }

    public static BigDecimal computeInvoiceBalance(@Nullable final Iterable<InvoiceItem> invoiceItems,
                                                   @Nullable final Iterable<InvoicePayment> invoicePayments) {
        return computeInvoiceAmountCharged(invoiceItems)
                .add(computeInvoiceAmountCredited(invoiceItems))
                .add(computeInvoiceAmountAdjustedForAccountCredit(invoiceItems))
                .add(
                        computeInvoiceAmountPaid(invoicePayments).negate()
                                .add(
                                        computeInvoiceAmountRefunded(invoicePayments).negate()
                                    )
                    )
                .setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    // Snowflake for the CREDIT_ADJ on its own invoice
    private static BigDecimal computeInvoiceAmountAdjustedForAccountCredit(final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountAdjusted = BigDecimal.ZERO;
        if (invoiceItems == null) {
            return amountAdjusted;
        }

        for (final InvoiceItem invoiceItem : invoiceItems) {
            final Iterable<InvoiceItem> otherInvoiceItems = Iterables.filter(invoiceItems, new Predicate<InvoiceItem>() {
                @Override
                public boolean apply(final InvoiceItem input) {
                    return !input.getId().equals(invoiceItem.getId());
                }
            });

            if (InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                (Iterables.size(otherInvoiceItems) == 1 &&
                 InvoiceItemType.CBA_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                 otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                 otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0)) {
                amountAdjusted = amountAdjusted.add(invoiceItem.getAmount());
            }
        }
        return amountAdjusted.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static BigDecimal computeInvoiceAmountCharged(@Nullable final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountCharged = BigDecimal.ZERO;
        if (invoiceItems == null) {
            return amountCharged;
        }

        for (final InvoiceItem invoiceItem : invoiceItems) {
            final Iterable<InvoiceItem> otherInvoiceItems = Iterables.filter(invoiceItems, new Predicate<InvoiceItem>() {
                @Override
                public boolean apply(final InvoiceItem input) {
                    return !input.getId().equals(invoiceItem.getId());
                }
            });

            if (isCharge(invoiceItem) ||
                isInvoiceAdjustmentItem(invoiceItem, otherInvoiceItems) ||
                isInvoiceItemAdjustmentItem(invoiceItem)) {
                amountCharged = amountCharged.add(invoiceItem.getAmount());
            }
        }
        return amountCharged.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static BigDecimal computeInvoiceOriginalAmountCharged(final DateTime invoiceCreatedDate, @Nullable final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountCharged = BigDecimal.ZERO;
        if (invoiceItems == null) {
            return amountCharged;
        }

        for (final InvoiceItem invoiceItem : invoiceItems) {
            if (isCharge(invoiceItem) &&
                invoiceItem.getCreatedDate().equals(invoiceCreatedDate)) {
                amountCharged = amountCharged.add(invoiceItem.getAmount());
            }
        }
        return amountCharged.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static BigDecimal computeInvoiceAmountCredited(@Nullable final Iterable<InvoiceItem> invoiceItems) {
        BigDecimal amountCredited = BigDecimal.ZERO;
        if (invoiceItems == null) {
            return amountCredited;
        }

        for (final InvoiceItem invoiceItem : invoiceItems) {
            if (isAccountCreditItem(invoiceItem)) {
                amountCredited = amountCredited.add(invoiceItem.getAmount());
            }
        }
        return amountCredited.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static BigDecimal computeInvoiceAmountPaid(@Nullable final Iterable<InvoicePayment> invoicePayments) {
        BigDecimal amountPaid = BigDecimal.ZERO;
        if (invoicePayments == null) {
            return amountPaid;
        }

        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (InvoicePaymentType.ATTEMPT.equals(invoicePayment.getType())) {
                amountPaid = amountPaid.add(invoicePayment.getAmount());
            }
        }
        return amountPaid.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static BigDecimal computeInvoiceAmountRefunded(@Nullable final Iterable<InvoicePayment> invoicePayments) {
        BigDecimal amountRefunded = BigDecimal.ZERO;
        if (invoicePayments == null) {
            return amountRefunded;
        }

        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (InvoicePaymentType.REFUND.equals(invoicePayment.getType()) ||
                InvoicePaymentType.CHARGED_BACK.equals(invoicePayment.getType())) {
                amountRefunded = amountRefunded.add(invoicePayment.getAmount());
            }
        }
        return amountRefunded.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }
}
