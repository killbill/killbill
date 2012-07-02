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

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.generator.DefaultInvoiceGenerator;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.io.IOUtils;
import com.ning.billing.util.tag.api.DefaultTagUserApi;
import com.ning.billing.util.tag.api.user.TagEventBuilder;
import com.ning.billing.util.tag.dao.AuditedTagDao;
import com.ning.billing.util.tag.dao.MockTagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class InvoiceDaoTestBase extends InvoicingTestBase {
    protected final TagEventBuilder tagEventBuilder = new TagEventBuilder();

    protected IDBI dbi;
    private MysqlTestingHelper mysqlTestingHelper;
    protected InvoiceDao invoiceDao;
    protected InvoiceItemSqlDao invoiceItemSqlDao;
    protected InvoicePaymentSqlDao invoicePaymentDao;
    protected Clock clock;
    protected CallContext context;
    protected InvoiceGenerator generator;
    protected Bus bus;

    private final InvoiceConfig invoiceConfig = new InvoiceConfig() {
        @Override
        public long getSleepTimeMs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isNotificationProcessingOff() {
            throw new UnsupportedOperationException();
        }

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

    @BeforeClass(groups={"slow"})
    protected void setup() throws IOException {

        loadSystemPropertiesFromClasspath("/resource.properties");

        mysqlTestingHelper = new MysqlTestingHelper();
        dbi = mysqlTestingHelper.getDBI();

        final String invoiceDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String utilDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        clock = new ClockMock();

        mysqlTestingHelper.startMysql();
        mysqlTestingHelper.initDb(invoiceDdl);
        mysqlTestingHelper.initDb(utilDdl);

        bus = new InMemoryBus();
        bus.start();

        final NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        final TagDefinitionDao tagDefinitionDao = new MockTagDefinitionDao();
        final TagDao tagDao = new AuditedTagDao(dbi, tagEventBuilder, bus);
        final TagUserApi tagUserApi = new DefaultTagUserApi(tagDefinitionDao, tagDao);
        invoiceDao = new DefaultInvoiceDao(dbi, nextBillingDatePoster, tagUserApi);
        invoiceDao.test();

        invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        invoicePaymentDao = dbi.onDemand(InvoicePaymentSqlDao.class);



        context = new TestCallContext("Invoice Dao Tests");
        generator = new DefaultInvoiceGenerator(clock, invoiceConfig);



        assertTrue(true);
    }

    @BeforeMethod(groups={"slow"})
    public void cleanupData() {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle h, final TransactionStatus status)
                    throws Exception {
                h.execute("truncate table invoices");
                h.execute("truncate table invoice_items");
                h.execute("truncate table invoice_payments");
                return null;
            }
        });
    }

    @AfterClass(groups={"slow"})
    protected void tearDown() {
        bus.stop();
        mysqlTestingHelper.stopMysql();
        assertTrue(true);
    }
}
