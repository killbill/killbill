/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DefaultAuditLogsForInvoices implements AuditLogsForInvoices {

    private final Map<UUID, List<AuditLog>> invoiceAuditLogs;
    private final Map<UUID, List<AuditLog>> invoiceItemsAuditLogs;

    public DefaultAuditLogsForInvoices(final Map<UUID, List<AuditLog>> invoiceAuditLogs, final Map<UUID, List<AuditLog>> invoiceItemsAuditLogs) {
        this.invoiceAuditLogs = invoiceAuditLogs;
        this.invoiceItemsAuditLogs = invoiceItemsAuditLogs;
    }

    @Override
    public Map<UUID, List<AuditLog>> getInvoiceAuditLogs() {
        return invoiceAuditLogs;
    }

    @Override
    public Map<UUID, List<AuditLog>> getInvoiceItemsAuditLogs() {
        return invoiceItemsAuditLogs;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultAuditLogsForInvoices");
        sb.append("{invoiceAuditLogs=").append(invoiceAuditLogs);
        sb.append(", invoiceItemsAuditLogs=").append(invoiceItemsAuditLogs);
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

        final DefaultAuditLogsForInvoices that = (DefaultAuditLogsForInvoices) o;

        if (invoiceAuditLogs != null ? !invoiceAuditLogs.equals(that.invoiceAuditLogs) : that.invoiceAuditLogs != null) {
            return false;
        }
        if (invoiceItemsAuditLogs != null ? !invoiceItemsAuditLogs.equals(that.invoiceItemsAuditLogs) : that.invoiceItemsAuditLogs != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceAuditLogs != null ? invoiceAuditLogs.hashCode() : 0;
        result = 31 * result + (invoiceItemsAuditLogs != null ? invoiceItemsAuditLogs.hashCode() : 0);
        return result;
    }
}
