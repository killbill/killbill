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

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;

import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.util.CallContext;
import com.ning.billing.util.CallOrigin;
import com.ning.billing.util.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.DefaultCallContext;
import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import org.testng.annotations.BeforeMethod;

public abstract class InvoiceDaoTestBase extends InvoicingTestBase {
    protected InvoiceDao invoiceDao;
    protected RecurringInvoiceItemSqlDao recurringInvoiceItemDao;
    protected InvoicePaymentSqlDao invoicePaymentDao;
    protected InvoiceModuleWithEmbeddedDb module;
    protected Clock clock;
    protected CallContext context;
    protected InvoiceGenerator generator;

    private final InvoiceConfig invoiceConfig = new InvoiceConfig() {
        @Override
        public long getDaoClaimTimeMs() {throw new UnsupportedOperationException();}
        @Override
        public int getDaoMaxReadyEvents() {throw new UnsupportedOperationException();}
        @Override
        public long getNotificationSleepTimeMs() {throw new UnsupportedOperationException();}
        @Override
        public boolean isEventProcessingOff() {throw new UnsupportedOperationException();}
        @Override
        public int getNumberOfMonthsInFuture() {return 36;}
    };

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        try {
            module = new InvoiceModuleWithEmbeddedDb();
            final String accountDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
            final String invoiceDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
            final String entitlementDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));

            module.startDb();
            module.initDb(accountDdl);
            module.initDb(invoiceDdl);
            module.initDb(entitlementDdl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);

            invoiceDao = injector.getInstance(InvoiceDao.class);
            invoiceDao.test();

            recurringInvoiceItemDao = module.getInvoiceItemSqlDao();

            invoicePaymentDao = module.getInvoicePaymentSqlDao();
            clock = injector.getInstance(Clock.class);
            context = new DefaultCallContext(clock, "Count Rogan", CallOrigin.TEST, UserType.TEST);
            generator = new DefaultInvoiceGenerator(clock, invoiceConfig);

            BusService busService = injector.getInstance(BusService.class);
            ((DefaultBusService) busService).startBus();

            assertTrue(true);
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void cleanupData() {
        module.getDbi().inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(Handle h, TransactionStatus status)
                    throws Exception {
                h.execute("truncate table accounts");
                //h.execute("truncate table entitlement_events");
                //h.execute("truncate table subscriptions");
                //h.execute("truncate table bundles");
                //h.execute("truncate table notifications");
                //h.execute("truncate table claimed_notifications");
                h.execute("truncate table invoices");
                h.execute("truncate table fixed_invoice_items");
                h.execute("truncate table recurring_invoice_items");
                //h.execute("truncate table tag_definitions");
                //h.execute("truncate table tags");
                //h.execute("truncate table custom_fields");
                //h.execute("truncate table invoice_payments");
                //h.execute("truncate table payment_attempts");
                //h.execute("truncate table payments");

                return null;
            }
        });
    }

    @AfterClass(alwaysRun = true)
    protected void tearDown() {
        module.stopDb();
        assertTrue(true);
    }
}
