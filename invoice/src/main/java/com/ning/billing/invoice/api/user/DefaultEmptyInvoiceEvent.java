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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.joda.time.DateTime;

import com.ning.billing.invoice.api.EmptyInvoiceEvent;

public class DefaultEmptyInvoiceEvent implements EmptyInvoiceEvent {

    private final UUID accountId;
    private final DateTime processingDate;
    private final UUID userToken;

    
    @JsonCreator
    public DefaultEmptyInvoiceEvent(@JsonProperty("accountId") final UUID accountId,
            @JsonProperty("processingDate") final DateTime processingDate,
            @JsonProperty("userToken") final UUID userToken) {
        super();
        this.accountId = accountId;
        this.processingDate = processingDate;
        this.userToken = userToken;
    }

    @JsonIgnore
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result
                + ((processingDate == null) ? 0 : processingDate.hashCode());
        result = prime * result
                + ((userToken == null) ? 0 : userToken.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultEmptyInvoiceEvent other = (DefaultEmptyInvoiceEvent) obj;
        if (accountId == null) {
            if (other.accountId != null)
                return false;
        } else if (!accountId.equals(other.accountId))
            return false;
        if (processingDate == null) {
            if (other.processingDate != null)
                return false;
        } else if (processingDate.compareTo(other.processingDate) != 0)
            return false;
        if (userToken == null) {
            if (other.userToken != null)
                return false;
        } else if (!userToken.equals(other.userToken))
            return false;
        return true;
    }
    
    
}
