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

package com.ning.billing.osgi.bundles.analytics.utils;

import java.math.BigDecimal;
import java.util.Collection;

import javax.annotation.Nullable;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao.BusinessInvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;

public class BusinessInvoiceUtils {

    public static Boolean isRevenueRecognizable(final InvoiceItem invoiceItem, final Collection<InvoiceItem> otherInvoiceItems) {
        // All items are recognizable except user generated credit (CBA_ADJ and CREDIT_ADJ on their own invoice)
        return !(InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                 (otherInvoiceItems.size() == 1 &&
                  InvoiceItemType.CREDIT_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                  otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                  otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0));
    }

    // Invoice adjustments
    public static boolean isInvoiceAdjustmentItem(final InvoiceItem invoiceItem, final Collection<InvoiceItem> otherInvoiceItems) {
        // Either REFUND_ADJ
        return InvoiceItemType.REFUND_ADJ.equals(invoiceItem.getInvoiceItemType()) ||
               // Or invoice level credit, i.e. credit adj, but NOT on its on own invoice
               // Note: the negative credit adj items (internal generation of account level credits) doesn't figure in analytics
               (InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                !(otherInvoiceItems.size() == 1 &&
                  InvoiceItemType.CBA_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                  otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                  otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0));
    }

    // Item adjustments
    public static boolean isInvoiceItemAdjustmentItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType());
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

    public static BigDecimal computeInvoiceBalance(@Nullable final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems,
                                                   @Nullable final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments) {
        return computeInvoiceAmountCharged(businessInvoiceItems)
                .add(computeInvoiceAmountCredited(businessInvoiceItems))
                .add(
                        computeInvoiceAmountPaid(businessInvoicePayments).negate()
                                .add(computeInvoiceAmountRefunded(businessInvoicePayments).negate())
                    );
    }

    public static BigDecimal computeInvoiceAmountCharged(@Nullable final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems) {
        BigDecimal amountCharged = BigDecimal.ZERO;
        if (businessInvoiceItems == null) {
            return amountCharged;
        }

        for (final BusinessInvoiceItemBaseModelDao businessInvoiceItem : businessInvoiceItems) {
            if (BusinessInvoiceItemType.CHARGE.equals(businessInvoiceItem.getBusinessInvoiceItemType()) ||
                BusinessInvoiceItemType.INVOICE_ADJUSTMENT.equals(businessInvoiceItem.getBusinessInvoiceItemType()) ||
                BusinessInvoiceItemType.INVOICE_ITEM_ADJUSTMENT.equals(businessInvoiceItem.getBusinessInvoiceItemType())) {
                amountCharged = amountCharged.add(businessInvoiceItem.getAmount());
            }
        }
        return amountCharged;
    }

    public static BigDecimal computeInvoiceOriginalAmountCharged(@Nullable final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems) {
        BigDecimal amountCharged = BigDecimal.ZERO;
        if (businessInvoiceItems == null) {
            return amountCharged;
        }

        for (final BusinessInvoiceItemBaseModelDao businessInvoiceItem : businessInvoiceItems) {
            if (BusinessInvoiceItemType.CHARGE.equals(businessInvoiceItem.getBusinessInvoiceItemType()) &&
                businessInvoiceItem.getCreatedDate().equals(businessInvoiceItem.getInvoiceCreatedDate())) {
                amountCharged = amountCharged.add(businessInvoiceItem.getAmount());
            }
        }
        return amountCharged;
    }

    public static BigDecimal computeInvoiceAmountCredited(@Nullable final Collection<BusinessInvoiceItemBaseModelDao> businessInvoiceItems) {
        BigDecimal amountCredited = BigDecimal.ZERO;
        if (businessInvoiceItems == null) {
            return amountCredited;
        }

        for (final BusinessInvoiceItemBaseModelDao businessInvoiceItem : businessInvoiceItems) {
            if (BusinessInvoiceItemType.ACCOUNT_CREDIT.equals(businessInvoiceItem.getBusinessInvoiceItemType())) {
                amountCredited = amountCredited.add(businessInvoiceItem.getAmount());
            }
        }
        return amountCredited;
    }

    public static BigDecimal computeInvoiceAmountPaid(@Nullable final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments) {
        BigDecimal amountPaid = BigDecimal.ZERO;
        if (businessInvoicePayments == null) {
            return amountPaid;
        }

        for (final BusinessInvoicePaymentBaseModelDao businessInvoicePayment : businessInvoicePayments) {
            if (InvoicePaymentType.ATTEMPT.toString().equals(businessInvoicePayment.getInvoicePaymentType())) {
                amountPaid = amountPaid.add(businessInvoicePayment.getAmount());
            }
        }
        return amountPaid;
    }

    public static BigDecimal computeInvoiceAmountRefunded(@Nullable final Collection<BusinessInvoicePaymentBaseModelDao> businessInvoicePayments) {
        BigDecimal amountRefunded = BigDecimal.ZERO;
        if (businessInvoicePayments == null) {
            return amountRefunded;
        }

        for (final BusinessInvoicePaymentBaseModelDao businessInvoicePayment : businessInvoicePayments) {
            if (InvoicePaymentType.REFUND.toString().equals(businessInvoicePayment.getInvoicePaymentType()) ||
                InvoicePaymentType.CHARGED_BACK.toString().equals(businessInvoicePayment.getInvoicePaymentType())) {
                amountRefunded = amountRefunded.add(businessInvoicePayment.getAmount());
            }
        }
        return amountRefunded;
    }
}
