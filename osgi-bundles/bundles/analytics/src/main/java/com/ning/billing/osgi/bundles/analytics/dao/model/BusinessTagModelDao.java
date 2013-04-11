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

import org.joda.time.DateTime;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public abstract class BusinessTagModelDao extends BusinessModelDaoBase {

    protected static final String ACCOUNT_TAGS_TABLE_NAME = "bac_tags";
    protected static final String INVOICE_PAYMENT_TAGS_TABLE_NAME = "bip_tags";
    protected static final String INVOICE_TAGS_TABLE_NAME = "bin_tags";

    public static final String[] ALL_TAGS_TABLE_NAMES = new String[]{ACCOUNT_TAGS_TABLE_NAME, INVOICE_PAYMENT_TAGS_TABLE_NAME, INVOICE_TAGS_TABLE_NAME};

    private Long tagRecordId;
    private String name;

    public static BusinessTagModelDao create(final Account account,
                                             final Long accountRecordId,
                                             final Tag tag,
                                             final Long tagRecordId,
                                             final TagDefinition tagDefinition,
                                             final AuditLog creationAuditLog,
                                             final Long tenantRecordId,
                                             @Nullable final ReportGroup reportGroup) {
        if (ObjectType.ACCOUNT.equals(tag.getObjectType())) {
            return new BusinessAccountTagModelDao(account,
                                                  accountRecordId,
                                                  tag,
                                                  tagRecordId,
                                                  tagDefinition,
                                                  creationAuditLog,
                                                  tenantRecordId,
                                                  reportGroup);
        } else if (ObjectType.INVOICE_PAYMENT.equals(tag.getObjectType())) {
            return new BusinessInvoicePaymentTagModelDao(account,
                                                         accountRecordId,
                                                         tag,
                                                         tagRecordId,
                                                         tagDefinition,
                                                         creationAuditLog,
                                                         tenantRecordId,
                                                         reportGroup);
        } else if (ObjectType.INVOICE.equals(tag.getObjectType())) {
            return new BusinessInvoiceTagModelDao(account,
                                                  accountRecordId,
                                                  tag,
                                                  tagRecordId,
                                                  tagDefinition,
                                                  creationAuditLog,
                                                  tenantRecordId,
                                                  reportGroup);
        } else {
            // We don't care
            return null;
        }
    }

    public BusinessTagModelDao() { /* When reading from the database */ }

    public BusinessTagModelDao(final Long tagRecordId,
                               final String name,
                               final DateTime createdDate,
                               final String createdBy,
                               final String createdReasonCode,
                               final String createdComments,
                               final UUID accountId,
                               final String accountName,
                               final String accountExternalKey,
                               final Long accountRecordId,
                               final Long tenantRecordId,
                               @Nullable final ReportGroup reportGroup) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId,
              reportGroup);
        this.tagRecordId = tagRecordId;
        this.name = name;
    }

    public BusinessTagModelDao(final Account account,
                               final Long accountRecordId,
                               final Tag tag,
                               final Long tagRecordId,
                               final TagDefinition tagDefinition,
                               final AuditLog creationAuditLog,
                               final Long tenantRecordId,
                               @Nullable final ReportGroup reportGroup) {
        this(tagRecordId,
             tagDefinition.getName(),
             tag.getCreatedDate(),
             creationAuditLog.getUserName(),
             creationAuditLog.getReasonCode(),
             creationAuditLog.getComment(),
             account.getId(),
             account.getName(),
             account.getExternalKey(),
             accountRecordId,
             tenantRecordId,
             reportGroup);
    }

    public Long getTagRecordId() {
        return tagRecordId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessTagModelDao");
        sb.append("{tagRecordId=").append(tagRecordId);
        sb.append(", name='").append(name).append('\'');
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

        final BusinessTagModelDao that = (BusinessTagModelDao) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (tagRecordId != null ? !tagRecordId.equals(that.tagRecordId) : that.tagRecordId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tagRecordId != null ? tagRecordId.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
