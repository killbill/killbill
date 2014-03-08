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

import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultInvoiceAdjustmentEvent extends BusEventBase implements InvoiceAdjustmentInternalEvent {

    private final UUID invoiceId;
    private final UUID accountId;

    @JsonCreator
    public DefaultInvoiceAdjustmentEvent(@JsonProperty("invoiceId") final UUID invoiceId,
                                         @JsonProperty("accountId") final UUID accountId,
                                         @JsonProperty("searchKey1") final Long searchKey1,
                                         @JsonProperty("searchKey2") final Long searchKey2,
                                         @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.invoiceId = invoiceId;
        this.accountId = accountId;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.INVOICE_ADJUSTMENT;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultInvoiceAdjustmentEvent");
        sb.append("{invoiceId=").append(invoiceId);
        sb.append(", accountId=").append(accountId);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultInvoiceAdjustmentEvent that = (DefaultInvoiceAdjustmentEvent) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        return result;
    }
}
