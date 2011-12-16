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

package com.ning.billing.invoice.api;

import com.google.inject.Inject;
import com.ning.billing.lifecycle.LifecycleHandlerType;

public class DefaultInvoiceService implements InvoiceService {
    private static final String INVOICE_SERVICE_NAME = "invoice-service";
    //private final InvoiceUserApi userApi;
    private final InvoicePaymentApi paymentApi;

    @Inject
    public DefaultInvoiceService(InvoicePaymentApi paymentApi) {
        //this.userApi = userApi;
        this.paymentApi = paymentApi;
    }

    @Override
    public String getName() {
        return INVOICE_SERVICE_NAME;
    }

    @Override
    public InvoiceUserApi getUserApi() {
        return null;
    }

    @Override
    public InvoicePaymentApi getPaymentApi() {
        return paymentApi;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.INIT_SERVICE)
    public void initialize() {
    }
}
