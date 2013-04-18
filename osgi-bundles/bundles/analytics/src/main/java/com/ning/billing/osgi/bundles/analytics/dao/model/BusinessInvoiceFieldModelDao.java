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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.account.api.Account;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.customfield.CustomField;

public class BusinessInvoiceFieldModelDao extends BusinessFieldModelDao {

    private UUID invoiceId;

    public BusinessInvoiceFieldModelDao() { /* When reading from the database */ }

    public BusinessInvoiceFieldModelDao(final Account account,
                                        final Long accountRecordId,
                                        final CustomField customField,
                                        final Long customFieldRecordId,
                                        @Nullable final AuditLog creationAuditLog,
                                        final Long tenantRecordId,
                                        @Nullable final ReportGroup reportGroup) {
        super(account,
              accountRecordId,
              customField,
              customFieldRecordId,
              creationAuditLog,
              tenantRecordId,
              reportGroup);
        this.invoiceId = customField.getObjectId();
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
