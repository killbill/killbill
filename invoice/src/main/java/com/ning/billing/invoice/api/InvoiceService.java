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
import com.ning.billing.lifecycle.LyfecycleHandlerType;

public class InvoiceService implements IInvoiceService {
    private static final String INVOICE_SERVICE_NAME = "invoice-service";
    private final IInvoiceUserApi userApi;
    private final IInvoicePaymentApi paymentApi;

    @Inject
    public InvoiceService(IInvoiceUserApi userApi, IInvoicePaymentApi paymentApi) {
        this.userApi = userApi;
        this.paymentApi = paymentApi;
    }

    @Override
    public String getName() {
        return INVOICE_SERVICE_NAME;
    }

    @Override
    public IInvoiceUserApi getUserApi() {
        return userApi;
    }

    @Override
    public IInvoicePaymentApi getPaymentApi() {
        return paymentApi;
    }

    @LyfecycleHandlerType(LyfecycleHandlerType.LyfecycleLevel.INIT_SERVICE)
    public void initialize() {
    }
}
