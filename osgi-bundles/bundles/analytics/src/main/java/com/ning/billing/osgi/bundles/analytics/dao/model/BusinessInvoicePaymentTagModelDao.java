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

import com.ning.billing.account.api.Account;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public class BusinessInvoicePaymentTagModelDao extends BusinessTagModelDao {

    private final UUID invoicePaymentId;

    public BusinessInvoicePaymentTagModelDao(final Account account,
                                             final Long accountRecordId,
                                             final Tag tag,
                                             final Long tagRecordId,
                                             final TagDefinition tagDefinition,
                                             final AuditLog creationAuditLog,
                                             final Long tenantRecordId) {
        super(account,
              accountRecordId,
              tag,
              tagRecordId,
              tagDefinition,
              creationAuditLog,
              tenantRecordId);
        this.invoicePaymentId = tag.getObjectId();
    }

    @Override
    public String getTableName() {
        return INVOICE_PAYMENT_TAGS_TABLE_NAME;
    }

    public UUID getInvoicePaymentId() {
        return invoicePaymentId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoicePaymentTagModelDao");
        sb.append("{invoicePaymentId=").append(invoicePaymentId);
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

        final BusinessInvoicePaymentTagModelDao that = (BusinessInvoicePaymentTagModelDao) o;

        if (invoicePaymentId != null ? !invoicePaymentId.equals(that.invoicePaymentId) : that.invoicePaymentId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (invoicePaymentId != null ? invoicePaymentId.hashCode() : 0);
        return result;
    }
}
