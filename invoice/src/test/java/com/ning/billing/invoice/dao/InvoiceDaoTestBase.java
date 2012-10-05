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

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;

import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.invoice.generator.DefaultInvoiceGenerator;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

public class InvoiceDaoTestBase extends InvoicingTestBase {
    protected final TagEventBuilder tagEventBuilder = new TagEventBuilder();

    protected IDBI dbi;
    protected InvoiceDao invoiceDao;
    protected InvoiceItemSqlDao invoiceItemSqlDao;
    protected InvoicePaymentSqlDao invoicePaymentDao;
    protected Clock clock;
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

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {

        loadSystemPropertiesFromClasspath("/resource.properties");

        final MysqlTestingHelper mysqlTestingHelper = KillbillTestSuiteWithEmbeddedDB.getMysqlTestingHelper();
        dbi = mysqlTestingHelper.getDBI();

        clock = new ClockMock();

        bus = new InMemoryBus();
        bus.start();

        final NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        invoiceDao = new AuditedInvoiceDao(dbi, nextBillingDatePoster, clock, bus);
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
}
