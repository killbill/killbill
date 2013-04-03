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

package com.ning.billing.osgi.bundles.analytics.model;

import java.util.UUID;

import org.joda.time.DateTime;

public class BusinessInvoiceFieldModelDao extends BusinessFieldModelDao {

    private static final String INVOICE_FIELDS_TABLE_NAME = "bin_fields";

    private final UUID invoiceId;

    public BusinessInvoiceFieldModelDao(final Long customFieldRecordId,
                                        final UUID invoiceId,
                                        final String name,
                                        final String value,
                                        final DateTime createdDate,
                                        final String createdBy,
                                        final String createdReasonCode,
                                        final String createdComments,
                                        final UUID accountId,
                                        final String accountName,
                                        final String accountExternalKey,
                                        final Long accountRecordId,
                                        final Long tenantRecordId) {
        super(customFieldRecordId,
              name,
              value,
              createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId);
        this.invoiceId = invoiceId;
    }

    @Override
    public String getTableName() {
        return INVOICE_FIELDS_TABLE_NAME;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoiceFieldModelDao");
        sb.append("{invoiceId=").append(invoiceId);
        sb.append(", customFieldRecordId=").append(getCustomFieldRecordId());
        sb.append(", name='").append(getName()).append('\'');
        sb.append(", value='").append(getValue()).append('\'');
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
        if (!super.equals(o)) {
            return false;
        }

        final BusinessInvoiceFieldModelDao that = (BusinessInvoiceFieldModelDao) o;

        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        return result;
    }
}
