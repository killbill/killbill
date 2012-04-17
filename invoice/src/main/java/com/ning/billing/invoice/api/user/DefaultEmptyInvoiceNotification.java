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
package com.ning.billing.invoice.api.user;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.invoice.api.EmptyInvoiceEvent;

public class DefaultEmptyInvoiceNotification implements EmptyInvoiceEvent {

    private final UUID accountId;
    private final DateTime processingDate;
    private final UUID userToken;

    
    public DefaultEmptyInvoiceNotification(final UUID accountId,
            final DateTime processingDate, final UUID userToken) {
        super();
        this.accountId = accountId;
        this.processingDate = processingDate;
        this.userToken = userToken;
    }

    @Override
    public BusEventType getBusEventType() {
        return BusEventType.INVOICE_EMPTY;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public DateTime getProcessingDate() {
        return processingDate;
    }
}
