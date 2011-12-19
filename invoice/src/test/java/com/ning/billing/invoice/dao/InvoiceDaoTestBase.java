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
import org.testng.annotations.BeforeClass;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.invoice.glue.InvoiceModuleMock;
import com.ning.billing.util.eventbus.DefaultEventBusService;
import com.ning.billing.util.eventbus.EventBusService;

import static org.testng.Assert.fail;

public abstract class InvoiceDaoTestBase {
    protected InvoiceDao invoiceDao;

    @BeforeClass()
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            InvoiceModuleMock module = new InvoiceModuleMock();
            final String ddl = IOUtils.toString(DefaultInvoiceDao.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
            module.createDb(ddl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module);

            invoiceDao = injector.getInstance(InvoiceDao.class);
            invoiceDao.test();

            EventBusService busService = injector.getInstance(EventBusService.class);
            ((DefaultEventBusService) busService).startBus();
        }
        catch (Throwable t) {
            fail(t.toString());
        }
    }
}
