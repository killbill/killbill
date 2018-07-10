/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.invoice.generator;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.junction.BillingEventSet;

public interface InvoiceGenerator {
    InvoiceWithMetadata generateInvoice(ImmutableAccountData account, @Nullable BillingEventSet events, @Nullable Iterable<Invoice> existingInvoices,
                                        final UUID targetInvoiceId, LocalDate targetDate, Currency targetCurrency, final InternalCallContext context) throws InvoiceApiException;
}
