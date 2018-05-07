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

package org.killbill.billing.invoice.template.formatters;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatter;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory.ResourceBundleType;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.invoice.template.translator.DefaultInvoiceTranslator;
import org.killbill.billing.util.email.templates.MustacheTemplateEngine;
import org.killbill.billing.util.template.translation.TranslatorConfig;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestDefaultInvoiceFormatter extends InvoiceTestSuiteNoDB {

    private TranslatorConfig config;
    private MustacheTemplateEngine templateEngine;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        config = new ConfigurationObjectFactory(skifeConfigSource).build(TranslatorConfig.class);
        templateEngine = new MustacheTemplateEngine();
    }

    @Test(groups = "fast")
    public void testIgnoreZeroAdjustments() throws Exception {
        // Scenario: single item with payment
        // * $10 item
        // * $-10 CBA
        // * $10 CBA
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), BigDecimal.TEN, Currency.USD);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem = new CreditBalanceAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                                        fixedItem.getStartDate(), fixedItem.getAmount(),
                                                                                                        fixedItem.getCurrency());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem2 = new CreditBalanceAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                                         fixedItem.getStartDate(), fixedItem.getAmount().negate(),
                                                                                                         fixedItem.getCurrency());
        final Invoice invoice = new DefaultInvoice(fixedItem.getInvoiceId(), fixedItem.getAccountId(), null,
                                                   new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoice.addInvoiceItem(fixedItem);
        invoice.addInvoiceItem(creditBalanceAdjInvoiceItem);
        invoice.addInvoiceItem(creditBalanceAdjInvoiceItem2);

        // Check the scenario
        Assert.assertEquals(invoice.getBalance().doubleValue(), 10.00);
        Assert.assertEquals(invoice.getCreditedAmount().doubleValue(), 0.00);

        // Verify the merge
        final InvoiceFormatter formatter = new DefaultInvoiceFormatter(config, invoice, Locale.US, null, resourceBundleFactory, internalCallContext);
        final List<InvoiceItem> invoiceItems = formatter.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 1);
        Assert.assertEquals(invoiceItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        Assert.assertEquals(invoiceItems.get(0).getAmount().doubleValue(), 10.00);
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
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), BigDecimal.TEN, Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                   fixedItem.getStartDate(), fixedItem.getEndDate(),
                                                                                   fixedItem.getAmount().negate(), fixedItem.getCurrency(),
                                                                                   fixedItem.getId());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem = new CreditBalanceAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                                        fixedItem.getStartDate(), fixedItem.getAmount(),
                                                                                                        fixedItem.getCurrency());
        final CreditAdjInvoiceItem creditAdjInvoiceItem = new CreditAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                   fixedItem.getStartDate(), null, BigDecimal.ONE.negate(), fixedItem.getCurrency(), null);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem2 = new CreditBalanceAdjInvoiceItem(fixedItem.getInvoiceId(), fixedItem.getAccountId(),
                                                                                                         fixedItem.getStartDate(), creditAdjInvoiceItem.getAmount().negate(),
                                                                                                         fixedItem.getCurrency());
        final DefaultInvoice invoice = new DefaultInvoice(fixedItem.getInvoiceId(), fixedItem.getAccountId(), null,
                                                          new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);
        invoice.addInvoiceItem(fixedItem);
        invoice.addInvoiceItem(repairAdjInvoiceItem);
        invoice.addInvoiceItem(creditBalanceAdjInvoiceItem);
        invoice.addInvoiceItem(creditAdjInvoiceItem);
        invoice.addInvoiceItem(creditBalanceAdjInvoiceItem2);
        invoice.addPayment(new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice.getId(), clock.getUTCNow(), BigDecimal.TEN,
                                                     Currency.USD, Currency.USD, null, true));
        invoice.addPayment(new DefaultInvoicePayment(InvoicePaymentType.REFUND, UUID.randomUUID(), invoice.getId(), clock.getUTCNow(), BigDecimal.ONE.negate(),
                                                     Currency.USD, Currency.USD, null, true));
        // Check the scenario
        Assert.assertEquals(invoice.getBalance().doubleValue(), 1.00);
        Assert.assertEquals(invoice.getCreditedAmount().doubleValue(), 11.00);
        Assert.assertEquals(invoice.getRefundedAmount().doubleValue(), -1.00);

        // Verify the merge
        final InvoiceFormatter formatter = new DefaultInvoiceFormatter(config, invoice, Locale.US, null, resourceBundleFactory, internalCallContext);
        final List<InvoiceItem> invoiceItems = formatter.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 4);
        Assert.assertEquals(invoiceItems.get(0).getInvoiceItemType(), InvoiceItemType.FIXED);
        Assert.assertEquals(invoiceItems.get(0).getAmount().doubleValue(), 10.00);
        Assert.assertEquals(invoiceItems.get(1).getInvoiceItemType(), InvoiceItemType.REPAIR_ADJ);
        Assert.assertEquals(invoiceItems.get(1).getAmount().doubleValue(), -10.00);
        Assert.assertEquals(invoiceItems.get(2).getInvoiceItemType(), InvoiceItemType.CBA_ADJ);
        Assert.assertEquals(invoiceItems.get(2).getAmount().doubleValue(), 11.00);
        Assert.assertEquals(invoiceItems.get(3).getInvoiceItemType(), InvoiceItemType.CREDIT_ADJ);
        Assert.assertEquals(invoiceItems.get(3).getAmount().doubleValue(), -1.00);
    }

    @Test(groups = "fast")
    public void testFormattedAmountFranceAndEUR() throws Exception {
        final FixedPriceInvoiceItem fixedItemEUR = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
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

    @Test(groups = "fast")
    public void testFormattedAmountFranceAndOMR() throws Exception {
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), new BigDecimal("1499.958"), Currency.OMR);
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.OMR);
        invoice.addInvoiceItem(fixedItem);

        checkOutput(invoice,
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
                    "    <td class=\"amount\"><strong>1 499,958 ر.ع</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>0,000 ر.ع</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>1 499,958 ر.ع</strong></td>\n" +
                    "</tr>",
                    Locale.FRANCE);
    }

    @Test(groups = "fast")
    public void testFormattedAmountFranceAndJPY() throws Exception {
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), new BigDecimal("1500.00"), Currency.JPY);
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.JPY);
        invoice.addInvoiceItem(fixedItem);

        checkOutput(invoice,
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
                    "    <td class=\"amount\"><strong>1 500 ¥</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>0 ¥</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>1 500 ¥</strong></td>\n" +
                    "</tr>",
                    Locale.FRANCE);
    }

    @Test(groups = "fast")
    public void testFormattedAmountUSAndBTC() throws Exception {
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), new BigDecimal("1105.28843439"), Currency.BTC);
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.BTC);
        invoice.addInvoiceItem(fixedItem);

        checkOutput(invoice,
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
                    "    <td class=\"amount\"><strong>Ƀ1,105.28843439</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>Ƀ0.00000000</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>Ƀ1,105.28843439</strong></td>\n" +
                    "</tr>",
                    Locale.US);
    }

    @Test(groups = "fast")
    public void testFormattedAmountUSAndEUR() throws Exception {
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), new BigDecimal("2635.14"), Currency.EUR);
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.EUR);
        invoice.addInvoiceItem(fixedItem);

        checkOutput(invoice,
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
                    "    <td class=\"amount\"><strong>€2,635.14</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>€0.00</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>€2,635.14</strong></td>\n" +
                    "</tr>",
                    Locale.US);
    }

    @Test(groups = "fast")
    public void testFormattedAmountUSAndBRL() throws Exception {
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), new BigDecimal("2635.14"), Currency.BRL);
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.BRL);
        invoice.addInvoiceItem(fixedItem);

        checkOutput(invoice,
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
                    "    <td class=\"amount\"><strong>R$2,635.14</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>R$0.00</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>R$2,635.14</strong></td>\n" +
                    "</tr>",
                    Locale.US);
    }

    @Test(groups = "fast")
    public void testFormattedAmountUSAndGBP() throws Exception {
        final FixedPriceInvoiceItem fixedItem = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                          UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                          new LocalDate(), new BigDecimal("1499.95"), Currency.GBP);
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.GBP);
        invoice.addInvoiceItem(fixedItem);

        checkOutput(invoice,
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
                    "    <td class=\"amount\"><strong>£1,499.95</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>£0.00</strong></td>\n" +
                    "</tr>\n" +
                    "<tr>\n" +
                    "    <td class=\"amount\"><strong>£1,499.95</strong></td>\n" +
                    "</tr>",
                    Locale.US);
    }


    @Test(groups = "fast")
    public void testProcessedCurrencyExists() throws Exception {

        // Use InvoiceModelDao to build the invoice to be able to set the processedCurrency (No suitable CTOR for DefaultInvoice on purpose)
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(UUID.randomUUID(), new LocalDate(), new LocalDate(), Currency.BRL, false);
        invoiceModelDao.setProcessedCurrency(Currency.USD);

        final Invoice invoice = new DefaultInvoice(invoiceModelDao);

        checkOutput(invoice,
                    "{{#invoice.processedCurrency}}" +
                    "<tr>\n" +
                    "    <td class=\"processedCurrency\"><strong>{{invoice.processedCurrency}}</strong></td>\n" +
                    "</tr>\n" +
                    "{{/invoice.processedCurrency}}",
                    "<tr>\n" +
                    "    <td class=\"processedCurrency\"><strong>USD</strong></td>\n" +
                    "</tr>\n",
                    Locale.US);
    }

    @Test(groups = "fast")
    public void testProcessedCurrencyDoesNotExist() throws Exception {
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), UUID.randomUUID(), new Integer(234), new LocalDate(), new LocalDate(), Currency.USD, false, InvoiceStatus.COMMITTED);

        checkOutput(invoice,
                    "{{#invoice.processedCurrency}}" +
                    "<tr>\n" +
                    "    <td class=\"processedCurrency\"><strong>{{invoice.processedCurrency}}</strong></td>\n" +
                    "</tr>\n" +
                    "{{/invoice.processedCurrency}}",
                    "",
                    Locale.US);
    }

    // No Assertion, just to print the html
    @Test(groups = "fast")
    public void testForDisplay() throws Exception {

        final FixedPriceInvoiceItem fixedItemBRL = new FixedPriceInvoiceItem(UUID.randomUUID(), UUID.randomUUID(), null, null,
                                                                             UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                                             new LocalDate(), new BigDecimal("1499.95"), Currency.BRL);

        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), UUID.randomUUID(), new Integer(234), new LocalDate(), new LocalDate(), Currency.BRL,  false, InvoiceStatus.COMMITTED);
        invoice.addInvoiceItem(fixedItemBRL);

        final String template = "<html>\n" +
                                "    <head>\n" +
                                "        <style type=\"text/css\">\n" +
                                "            #header td { width: 250px; }\n" +
                                "            #header .company_info { width: 450px; }\n" +
                                "            #header .label { text-align: right; font-weight: bold; }\n" +
                                "            #header .label_value { padding-left: 10px; }\n" +
                                "\n" +
                                "            #invoice_items { margin-top: 50px; }\n" +
                                "            #invoice_items th { border-bottom: solid 2px black; }\n" +
                                "            #invoice_items td { width: 250px; }\n" +
                                "            #invoice_items td.amount { width: 125px; }\n" +
                                "            #invoice_items td.network { width: 350px; }\n" +
                                "            #invoice_items .amount { text-align: right; }\n" +
                                "            #invoice_items .label { text-align: right; font-weight: bold; }\n" +
                                "        </style>\n" +
                                "    </head>\n" +
                                "    <body>\n" +
                                "        <table id=\"header\">\n" +
                                "            <tr>\n" +
                                "                <td class=\"company_info\"/>\n" +
                                "                <td />\n" +
                                "                <td><h1>{{text.invoiceTitle}}</h1></td>\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td colspan=\"3\"><img src=\"http://static.foo.com/www/0/main/gfx/front/logo.png\"/></td>\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td class=\"company_info\" />\n" +
                                "                <td class=\"label\">{{text.invoiceDate}}</td>\n" +
                                "                <td class=\"label_value\">{{invoice.formattedInvoiceDate}}</td>\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td class=\"company_info\" />\n" +
                                "                <td class=\"label\">{{text.invoiceNumber}}</td>\n" +
                                "                <td class=\"label_value\">{{invoice.invoiceNumber}}</td>\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td class=\"company_info\">{{text.companyCountry}}</td>\n" +
                                "                <td colspan=\"2\" />\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td class=\"company_info\">{{text.companyUrl}}</td>\n" +
                                "                <td colspan=\"2\" />\n" +
                                "            </tr>\n" +
                                "        </table>\n" +
                                "\n" +
                                "        <table id=\"invoice_items\">\n" +
                                "            <tr>\n" +
                                "                <th class=\"network\">{{text.invoiceItemBundleName}}</td>\n" +
                                "                <th>{{text.invoiceItemDescription}}</td>\n" +
                                "                <th>{{text.invoiceItemServicePeriod}}</td>\n" +
                                "                <th>{{text.invoiceItemAmount}}</td>\n" +
                                "            </tr>\n" +
                                "            {{#invoice.invoiceItems}}\n" +
                                "            <tr>\n" +
                                "                <td class=\"network\">{{description}}</td>\n" +
                                "                <td>{{planName}}</td>\n" +
                                "                <td>{{formattedStartDate}}{{#formattedEndDate}} - {{formattedEndDate}}{{/formattedEndDate}}</td>\n" +
                                "                <td class=\"amount\">{{formattedAmount}}</td>\n" +
                                "            </tr>\n" +
                                "            {{/invoice.invoiceItems}}\n" +
                                "            <tr>\n" +
                                "                <td colspan=\"4\" height=\"50px\"></td>\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td colspan=\"2\" />\n" +
                                "                <td class=\"label\">{{text.invoiceAmount}}</td>\n" +
                                "                <td class=\"amount\"><strong>{{invoice.formattedChargedAmount}}</strong></td>\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td colspan=\"2\" />\n" +
                                "                <td class=\"label\">{{text.invoiceAmountPaid}}" +
                                "                    {{#invoice.processedCurrency}}" +
                                " (*)" +
                                "                    {{/invoice.processedCurrency}}\n" +
                                "                </td>\n" +
                                "                <td class=\"amount\"><strong>{{invoice.formattedPaidAmount}}</strong></td>\n" +
                                "            </tr>\n" +
                                "            <tr>\n" +
                                "                <td colspan=\"2\" />\n" +
                                "                <td class=\"label\">{{text.invoiceBalance}}</td>\n" +
                                "                <td class=\"amount\"><strong>{{invoice.formattedBalance}}</strong></td>\n" +
                                "            </tr>\n" +
                                "        </table>\n" +
                                "        {{#invoice.processedCurrency}}" +
                                "        {{text.processedPaymentCurrency}} {{invoice.processedCurrency}}." +
                                "        {{#invoice.processedPaymentRate}}\n" +
                                " {{text.processedPaymentRate}} {{invoice.processedPaymentRate}}.\n" +
                                "        {{/invoice.processedPaymentRate}}" +
                                "        {{/invoice.processedCurrency}}" +
                                "    </body>\n" +
                                "</html>\n";

        final Map<String, Object> data = new HashMap<String, Object>();

        final String bundlePath = "org/killbill/billing/util/template/translation/InvoiceTranslation";
        final ResourceBundle bundle = resourceBundleFactory.createBundle(Locale.US, bundlePath, ResourceBundleType.INVOICE_TRANSLATION, internalCallContext);

        final DefaultInvoiceTranslator translator = new DefaultInvoiceTranslator(bundle, null);

        data.put("text", translator);

        data.put("invoice", new DefaultInvoiceFormatter(config, invoice, Locale.US, currencyConversionApi, resourceBundleFactory, internalCallContext));

        final String formattedText = templateEngine.executeTemplateText(template, data);

        System.out.println(formattedText);
    }

    private void checkOutput(final Invoice invoice, final String template, final String expected, final Locale locale) {
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("invoice", new DefaultInvoiceFormatter(config, invoice, locale, null, resourceBundleFactory, internalCallContext));

        final String formattedText = templateEngine.executeTemplateText(template, data);
        Assert.assertEquals(formattedText, expected);
    }
}
