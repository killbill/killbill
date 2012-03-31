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

import com.ning.billing.util.callcontext.CallContext;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface InvoiceUserApi {
    public List<Invoice> getInvoicesByAccount(UUID accountId);

    public List<Invoice> getInvoicesByAccount(UUID accountId, DateTime fromDate);

    public BigDecimal getAccountBalance(UUID accountId);

    public Invoice getInvoice(UUID invoiceId);

    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment, CallContext context);

    public Collection<Invoice> getUnpaidInvoicesByAccountId(UUID accountId, DateTime upToDate);
    
    public Invoice triggerInvoiceGeneration(UUID accountId, DateTime targetDate, boolean dryRun, CallContext context) throws InvoiceApiException;

    public void tagInvoiceAsWrittenOff(UUID invoiceId, CallContext context);

    public void tagInvoiceAsNotWrittenOff(UUID invoiceId, CallContext context);
}
