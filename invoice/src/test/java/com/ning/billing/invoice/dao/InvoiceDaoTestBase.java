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

package com.ning.billing.invoice.dao;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.generator.DefaultInvoiceGenerator;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.util.bus.InMemoryInternalBus;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.InvoiceConfig;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class InvoiceDaoTestBase extends InvoicingTestBase {

    protected IDBI dbi;
    protected InvoiceDao invoiceDao;
    protected InvoiceItemSqlDao invoiceItemSqlDao;
    protected InvoicePaymentSqlDao invoicePaymentDao;
    protected Clock clock;
    protected InvoiceGenerator generator;
    protected InternalBus bus;
    protected CacheControllerDispatcher controllerDispatcher;


    private final InvoiceConfig invoiceConfig = new InvoiceConfig() {
        @Override
        public int getNumberOfMonthsInFuture() {
            return 36;
        }

        @Override
        public boolean isEmailNotificationsEnabled() {
            return false;
        }
    };

    private static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = InvoiceModuleWithEmbeddedDb.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {

        loadSystemPropertiesFromClasspath("/resource.properties");

        dbi = KillbillTestSuiteWithEmbeddedDB.getDBI();

        clock = new ClockMock();

        bus = new InMemoryInternalBus();
        bus.start();

        final NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        controllerDispatcher = new CacheControllerDispatcher();

        invoiceDao = new DefaultInvoiceDao(dbi, nextBillingDatePoster, bus, clock, controllerDispatcher, new DefaultNonEntityDao(dbi));
        invoiceDao.test(internalCallContext);

        invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        invoicePaymentDao = dbi.onDemand(InvoicePaymentSqlDao.class);

        generator = new DefaultInvoiceGenerator(clock, invoiceConfig);

        assertTrue(true);
    }

    @AfterClass(groups = "slow")
    protected void tearDown() {
        bus.stop();
        assertTrue(true);
    }

    protected void createPayment(final InvoicePayment invoicePayment, final InternalCallContext internalCallContext) {
        try {
            invoicePaymentDao.create(new InvoicePaymentModelDao(invoicePayment), internalCallContext);
        } catch (EntityPersistenceException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void createInvoiceItem(final InvoiceItem invoiceItem, final InternalCallContext internalCallContext) throws EntityPersistenceException {
        invoiceItemSqlDao.create(new InvoiceItemModelDao(invoiceItem), internalCallContext);
    }

    protected void createInvoice(final Invoice invoice, final boolean isRealInvoiceWithItems, final InternalCallContext internalCallContext) {
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
        final List<InvoiceItemModelDao> invoiceItemModelDaos = ImmutableList.<InvoiceItemModelDao>copyOf(Collections2.transform(invoice.getInvoiceItems(),
                                                                                                                                new Function<InvoiceItem, InvoiceItemModelDao>() {
                                                                                                                                    @Override
                                                                                                                                    public InvoiceItemModelDao apply(final InvoiceItem input) {
                                                                                                                                        return new InvoiceItemModelDao(input);
                                                                                                                                    }
                                                                                                                                }));
        // Not really needed, there shouldn't be any payment at this stage
        final List<InvoicePaymentModelDao> invoicePaymentModelDaos = ImmutableList.<InvoicePaymentModelDao>copyOf(Collections2.transform(invoice.getPayments(),
                                                                                                                                         new Function<InvoicePayment, InvoicePaymentModelDao>() {
                                                                                                                                             @Override
                                                                                                                                             public InvoicePaymentModelDao apply(final InvoicePayment input) {
                                                                                                                                                 return new InvoicePaymentModelDao(input);
                                                                                                                                             }
                                                                                                                                         }));

        // The test does not use the invoice callback notifier hence the empty map
        invoiceDao.createInvoice(invoiceModelDao, invoiceItemModelDaos, invoicePaymentModelDaos, isRealInvoiceWithItems, ImmutableMap.<UUID, DateTime>of(), internalCallContext);
    }

    protected void verifyInvoice(final UUID invoiceId, final double balance, final double cbaAmount) throws InvoiceApiException {
        final InvoiceModelDao invoice = invoiceDao.getById(invoiceId, internalCallContext);
        Assert.assertEquals(InvoiceModelDaoHelper.getBalance(invoice).doubleValue(), balance);
        Assert.assertEquals(InvoiceModelDaoHelper.getCBAAmount(invoice).doubleValue(), cbaAmount);
    }

    protected void checkInvoicesEqual(final InvoiceModelDao retrievedInvoice, final Invoice invoice) {
        Assert.assertEquals(retrievedInvoice.getId(), invoice.getId());
        Assert.assertEquals(retrievedInvoice.getAccountId(), invoice.getAccountId());
        Assert.assertEquals(retrievedInvoice.getCurrency(), invoice.getCurrency());
        Assert.assertEquals(retrievedInvoice.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(retrievedInvoice.getTargetDate(), invoice.getTargetDate());
        Assert.assertEquals(retrievedInvoice.getInvoiceItems().size(), invoice.getInvoiceItems().size());
        Assert.assertEquals(retrievedInvoice.getInvoicePayments().size(), invoice.getPayments().size());
    }
}
