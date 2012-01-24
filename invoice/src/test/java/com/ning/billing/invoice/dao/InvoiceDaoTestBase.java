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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.invoice.glue.InvoiceModuleWithEmbeddedDb;
import com.ning.billing.util.eventbus.BusService;
import com.ning.billing.util.eventbus.DefaultEventBusService;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.io.IOException;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public abstract class InvoiceDaoTestBase {
    protected InvoiceDao invoiceDao;
    protected InvoiceItemSqlDao invoiceItemDao;
    protected InvoicePaymentSqlDao invoicePaymentDao;
    protected InvoiceModuleWithEmbeddedDb module;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            module = new InvoiceModuleWithEmbeddedDb();
            final String invoiceDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
            final String entitlementDdl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));

            module.startDb();
            module.initDb(invoiceDdl);
            module.initDb(entitlementDdl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);

            invoiceDao = injector.getInstance(InvoiceDao.class);
            invoiceDao.test();

            invoiceItemDao = module.getInvoiceItemDao();

            invoicePaymentDao = module.getIDBI().onDemand(InvoicePaymentSqlDao.class);

            BusService busService = injector.getInstance(BusService.class);
            ((DefaultEventBusService) busService).startBus();

            assertTrue(true);
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    protected void tearDown() {
        module.stopDb();
        assertTrue(true);
    }
}
