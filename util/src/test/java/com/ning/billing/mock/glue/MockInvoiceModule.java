/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.mock.glue;

import org.mockito.Mockito;

import com.ning.billing.glue.InvoiceModule;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;

import com.google.inject.AbstractModule;

public class MockInvoiceModule extends AbstractModule implements InvoiceModule {
    @Override
    public void installInvoiceUserApi() {
        bind(InvoiceUserApi.class).toInstance(Mockito.mock(InvoiceUserApi.class));
    }

    @Override
    public void installInvoicePaymentApi() {
        bind(InvoicePaymentApi.class).toInstance(Mockito.mock(InvoicePaymentApi.class));
    }

    @Override
    public void installInvoiceMigrationApi() {
        bind(InvoiceMigrationApi.class).toInstance(Mockito.mock(InvoiceMigrationApi.class));
    }

    @Override
    protected void configure() {
        installInvoiceUserApi();
        installInvoicePaymentApi();
        installInvoiceMigrationApi();
    }

    @Override
    public void installInvoiceInternalApi() {
        bind(InvoiceInternalApi.class).toInstance(Mockito.mock(InvoiceInternalApi.class));
    }
}
