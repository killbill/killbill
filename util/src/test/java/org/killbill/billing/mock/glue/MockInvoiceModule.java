/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.mock.glue;

import org.killbill.billing.glue.InvoiceModule;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.KillBillModule;
import org.mockito.Mockito;

public class MockInvoiceModule extends KillBillModule implements InvoiceModule {

    public MockInvoiceModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    public void installInvoiceUserApi() {
        bind(InvoiceUserApi.class).toInstance(Mockito.mock(InvoiceUserApi.class));
    }

    @Override
    protected void configure() {
        installInvoiceUserApi();
        installInvoiceInternalApi();
    }

    @Override
    public void installInvoiceInternalApi() {
        bind(InvoiceInternalApi.class).toInstance(Mockito.mock(InvoiceInternalApi.class));
    }
}
