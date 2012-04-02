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

package com.ning.billing.invoice.api.migration;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.model.DefaultInvoice;
import org.joda.time.DateTime;

import java.util.UUID;

public class MigrationInvoice extends DefaultInvoice {
    public MigrationInvoice(UUID accountId, DateTime invoiceDate, DateTime targetDate, Currency currency) {
        super(UUID.randomUUID(), accountId, null, invoiceDate, targetDate, currency, true, null, null);
    }
}
