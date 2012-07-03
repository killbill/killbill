/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joda.time.DateTime;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;
import com.ning.billing.invoice.template.HtmlInvoiceGenerator;
import com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.email.templates.MustacheTemplateEngine;
import com.ning.billing.util.email.templates.TemplateEngine;
import com.ning.billing.util.template.translation.TranslatorConfig;

import static org.testng.Assert.assertNotNull;

public class TestHtmlInvoiceGenerator {
    private HtmlInvoiceGenerator g;

    @BeforeSuite(groups = {"fast"})
    public void setup() {
        final TranslatorConfig config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
        final TemplateEngine templateEngine = new MustacheTemplateEngine();
        final InvoiceFormatterFactory factory = new DefaultInvoiceFormatterFactory();
        g = new HtmlInvoiceGenerator(factory, templateEngine, config);
    }

    @Test(groups = {"fast"})
    public void testGenerateInvoice() throws Exception {
        final String output = g.generateInvoice(createAccount(), createInvoice());
        assertNotNull(output);
        System.out.print(output);
    }

    private Account createAccount() {
        final Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        final ZombieControl zombieControl = (ZombieControl) account;
        zombieControl.addResult("getExternalKey", "1234abcd");
        zombieControl.addResult("getName", "Jim Smith");
        zombieControl.addResult("getFirstNameLength", 3);
        zombieControl.addResult("getEmail", "jim.smith@mail.com");
        zombieControl.addResult("getLocale", Locale.US.toString());
        zombieControl.addResult("getAddress1", "123 Some Street");
        zombieControl.addResult("getAddress2", "Apt 456");
        zombieControl.addResult("getCity", "Some City");
        zombieControl.addResult("getStateOrProvince", "Some State");
        zombieControl.addResult("getPostalCode", "12345-6789");
        zombieControl.addResult("getCountry", "USA");
        zombieControl.addResult("getPhone", "123-456-7890");

        return account;
    }

    private Invoice createInvoice() {
        final DateTime startDate = new DateTime().minusMonths(1);
        final DateTime endDate = new DateTime();

        final BigDecimal price1 = new BigDecimal("29.95");
        final BigDecimal price2 = new BigDecimal("59.95");
        final Invoice dummyInvoice = BrainDeadProxyFactory.createBrainDeadProxyFor(Invoice.class);
        final ZombieControl zombie = (ZombieControl) dummyInvoice;
        zombie.addResult("getInvoiceDate", startDate);
        zombie.addResult("getInvoiceNumber", 42);
        zombie.addResult("getCurrency", Currency.USD);
        zombie.addResult("getChargedAmount", price1.add(price2));
        zombie.addResult("getPaidAmount", BigDecimal.ZERO);
        zombie.addResult("getBalance", price1.add(price2));

        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(createInvoiceItem(price1, "Domain 1", startDate, endDate, "ning-plus"));
        items.add(createInvoiceItem(price2, "Domain 2", startDate, endDate, "ning-pro"));
        zombie.addResult("getInvoiceItems", items);

        return dummyInvoice;
    }

    private InvoiceItem createInvoiceItem(final BigDecimal amount, final String networkName, final DateTime startDate, final DateTime endDate, final String planName) {
        final InvoiceItem item = BrainDeadProxyFactory.createBrainDeadProxyFor(InvoiceItem.class);
        final ZombieControl zombie = (ZombieControl) item;
        zombie.addResult("getAmount", amount);
        zombie.addResult("getStartDate", startDate);
        zombie.addResult("getEndDate", endDate);
        zombie.addResult("getPlanName", planName);
        zombie.addResult("getDescription", networkName);


        return item;
    }
}
