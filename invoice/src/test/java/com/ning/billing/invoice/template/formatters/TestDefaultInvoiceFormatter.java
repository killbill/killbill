/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.invoice.template.formatters;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuite;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.formatters.InvoiceFormatter;
import com.ning.billing.invoice.model.CreditAdjInvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.RefundAdjInvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.email.templates.MustacheTemplateEngine;
import com.ning.billing.util.template.translation.TranslatorConfig;

public class TestDefaultInvoiceFormatter extends InvoiceTestSuite {

    private TranslatorConfig config;
    private MustacheTemplateEngine templateEngine;

    @BeforeSuite(groups = "fast")
    public void setup() {
        config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
        templateEngine = new MustacheTemplateEngine();
    }

    @Test(groups = "fast")
    public void testMergeItems() throws Exception {
        // Scenario: single item with payment
        // * $10 item
        // Then, a repair occur:
        // * $-10 repair
        // * $10 generated CBA due to the repair (assume previous payment)
        // Then, the invoice is adjusted for $1:
        // * $-1 credit adjustment
        // * $1 generated CBA due to the credit adjustment
        // Then, we refund $1 with invoice level adjustment:
        // * $-1 refund adjustment
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), BigDecimal.TEN, Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                   fixedItem.getStartDate(), fixedItem.getEndDate(),
                                                                                   fixedItem.getAmount().negate(), fixedItem.getCurrency(),
                                                                                   fixedItem.getId());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem = new CreditBalanceAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                                        fixedItem.getStartDate(), fixedItem.getAmount(),
                                                                                                        fixedItem.getCurrency());
        final CreditAdjInvoiceItem creditAdjInvoiceItem = new CreditAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                   fixedItem.getStartDate(), BigDecimal.ONE.negate(), fixedItem.getCurrency());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem2 = new CreditBalanceAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                                         fixedItem.getStartDate(), creditAdjInvoiceItem.getAmount().negate(),
                                                                                                         fixedItem.getCurrency());
        final RefundAdjInvoiceItem refundAdjInvoiceItem = new RefundAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                   fixedItem.getStartDate(), BigDecimal.ONE.negate(), fixedItem.getCurrency());
        final Invoice invoice = new DefaultInvoice(fixedItem.getInvoiceId(), fixedItem.getAccountId(), null,
                                                   new LocalDate(), new LocalDate(), Currency.USD, false);
        invoice.addInvoiceItem(fixedItem);
        invoice.addInvoiceItem(repairAdjInvoiceItem);
        invoice.addInvoiceItem(creditBalanceAdjInvoiceItem);
        invoice.addInvoiceItem(creditAdjInvoiceItem);
        invoice.addInvoiceItem(creditBalanceAdjInvoiceItem2);
        invoice.addInvoiceItem(refundAdjInvoiceItem);
        // Check the scenario
        Assert.assertEquals(invoice.getBalance().doubleValue(), 9.00);
        Assert.assertEquals(invoice.getCBAAmount().doubleValue(), 11.00);
        Assert.assertEquals(invoice.getRefundAdjAmount().doubleValue(), -1.00);

        // Verify the merge
        final InvoiceFormatter formatter = new DefaultInvoiceFormatter(config, invoice, Locale.US);
        final List<InvoiceItem> invoiceItems = formatter.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 4);
        Assert.assertEquals(invoiceItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        Assert.assertEquals(invoiceItems.get(0).getAmount().doubleValue(), 10.00);
        Assert.assertEquals(invoiceItems.get(1).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        Assert.assertEquals(invoiceItems.get(1).getAmount().doubleValue(), -10.00);
        Assert.assertEquals(invoiceItems.get(2).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        Assert.assertEquals(invoiceItems.get(2).getAmount().doubleValue(), 11.00);
        Assert.assertEquals(invoiceItems.get(3).getInvoiceItemType(), InvoiceItemType.CREDIT_ADJ);
        Assert.assertEquals(invoiceItems.get(3).getAmount().doubleValue(), -2.00);
    }

    @Test(groups = "fast")
    public void testFormattedAmount() throws Exception {
        final FixedPriceInvoiceItem fixedItemEUR = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                             new LocalDate(), new BigDecimal("1499.95"), Currency.EUR);
        final Invoice invoiceEUR = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.EUR);
        invoiceEUR.addInvoiceItem(fixedItemEUR);

        checkOutput(invoiceEUR,
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>{{invoice.formattedChargedAmount}}</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>{{invoice.formattedPaidAmount}}</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>{{invoice.formattedBalance}}</strong></td>\n" +
                    "</tr>",
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>1 499,95 €</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>0,00 €</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>1 499,95 €</strong></td>\n" +
                    "</tr>",
                    Locale.FRANCE);
    }

    private void checkOutput(final Invoice invoice, final String template, final String expected, final Locale locale) {
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("invoice", new DefaultInvoiceFormatter(config, invoice, locale));

        final String formattedText = templateEngine.executeTemplateText(template, data);
        Assert.assertEquals(formattedText, expected);
    }
}
