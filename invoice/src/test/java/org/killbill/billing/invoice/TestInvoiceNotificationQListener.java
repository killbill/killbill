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

package org.killbill.billing.invoice;

import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.clock.Clock;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;

public class TestInvoiceNotificationQListener extends InvoiceListener {

    int eventCount = 0;
    UUID latestSubscriptionId = null;

    @Inject
    public TestInvoiceNotificationQListener(final AccountInternalApi accountApi, final Clock clock, final InternalCallContextFactory internalCallContextFactory, final InvoiceDispatcher dispatcher, final InvoiceInternalApi invoiceApi) {
        super(accountApi, clock, internalCallContextFactory, null, dispatcher, invoiceApi);
    }

    @Override
    public void handleNextBillingDateEvent(final UUID subscriptionId, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        eventCount++;
        latestSubscriptionId = subscriptionId;
    }

    public int getEventCount() {
        return eventCount;
    }

    public UUID getLatestSubscriptionId() {
        return latestSubscriptionId;
    }

}
