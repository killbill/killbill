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

import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceGenerator;
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
import com.ning.billing.util.tag.api.DefaultTagUserApi;
import com.ning.billing.util.tag.api.user.TagEventBuilder;
import com.ning.billing.util.tag.dao.AuditedTagDao;
import com.ning.billing.util.tag.dao.MockTagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import static org.testng.Assert.assertTrue;

public abstract class InvoiceDaoTestBase extends InvoicingTestBase {
    protected final TagEventBuilder tagEventBuilder = new TagEventBuilder();

    protected IDBI dbi;
    private MysqlTestingHelper mysqlTestingHelper;
    protected InvoiceDao invoiceDao;
    protected RecurringInvoiceItemSqlDao recurringInvoiceItemDao;
    protected FixedPriceInvoiceItemSqlDao fixedPriceInvoiceItemSqlDao;
    protected CreditInvoiceItemSqlDao creditInvoiceItemSqlDao;
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

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        mysqlTestingHelper = new MysqlTestingHelper();
        dbi = mysqlTestingHelper.getDBI();

        final String invoiceDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
        final String utilDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        mysqlTestingHelper.startMysql();
        mysqlTestingHelper.initDb(invoiceDdl);
        mysqlTestingHelper.initDb(utilDdl);

        final NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        final TagDefinitionDao tagDefinitionDao = new MockTagDefinitionDao();
        final TagDao tagDao = new AuditedTagDao(dbi, tagEventBuilder, bus);
        final TagUserApi tagUserApi = new DefaultTagUserApi(tagDefinitionDao, tagDao);
        invoiceDao = new DefaultInvoiceDao(dbi, nextBillingDatePoster, tagUserApi);
        invoiceDao.test();

        recurringInvoiceItemDao = dbi.onDemand(RecurringInvoiceItemSqlDao.class);
        fixedPriceInvoiceItemSqlDao = dbi.onDemand(FixedPriceInvoiceItemSqlDao.class);
        creditInvoiceItemSqlDao = dbi.onDemand(CreditInvoiceItemSqlDao.class);
        invoicePaymentDao = dbi.onDemand(InvoicePaymentSqlDao.class);

        clock = new ClockMock();
        context = new TestCallContext("Invoice Dao Tests");
        generator = new DefaultInvoiceGenerator(clock, invoiceConfig);
        bus = new InMemoryBus();
        bus.start();

        assertTrue(true);
    }

    @BeforeMethod(alwaysRun = true)
    public void cleanupData() {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle h, final TransactionStatus status)
                    throws Exception {
                h.execute("truncate table invoices");
                h.execute("truncate table fixed_invoice_items");
                h.execute("truncate table recurring_invoice_items");
                h.execute("truncate table credit_invoice_items");
                h.execute("truncate table invoice_payments");

                return null;
            }
        });
    }

    @AfterClass(alwaysRun = true)
    protected void tearDown() {
        bus.stop();
        mysqlTestingHelper.stopMysql();
        assertTrue(true);
    }
}
