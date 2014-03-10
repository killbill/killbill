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

package org.killbill.billing.invoice.api.user;

import java.util.UUID;

import org.joda.time.LocalDate;

import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.NullInvoiceInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultNullInvoiceEvent extends BusEventBase implements NullInvoiceInternalEvent {

    private final UUID accountId;
    private final LocalDate processingDate;

    @JsonCreator
    public DefaultNullInvoiceEvent(@JsonProperty("accountId") final UUID accountId,
                                   @JsonProperty("processingDate") final LocalDate processingDate,
                                   @JsonProperty("searchKey1") final Long searchKey1,
                                   @JsonProperty("searchKey2") final Long searchKey2,
                                   @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.accountId = accountId;
        this.processingDate = processingDate;

    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.INVOICE_EMPTY;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getProcessingDate() {
        return processingDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultNullInvoiceEvent");
        sb.append("{accountId=").append(accountId);
        sb.append(", processingDate=").append(processingDate);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result
                 + ((processingDate == null) ? 0 : processingDate.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultNullInvoiceEvent other = (DefaultNullInvoiceEvent) obj;
        if (accountId == null) {
            if (other.accountId != null) {
                return false;
            }
        } else if (!accountId.equals(other.accountId)) {
            return false;
        }
        if (processingDate == null) {
            if (other.processingDate != null) {
                return false;
            }
        } else if (processingDate.compareTo(other.processingDate) != 0) {
            return false;
        }
        return true;
    }
}
