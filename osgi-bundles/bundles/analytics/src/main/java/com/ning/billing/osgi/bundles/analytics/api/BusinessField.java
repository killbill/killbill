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

package com.ning.billing.osgi.bundles.analytics.api;

import com.ning.billing.ObjectType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentFieldModelDao;

public class BusinessField extends BusinessEntityBase {

    private final ObjectType objectType;
    private final String name;
    private final String value;

    private BusinessField(final ObjectType objectType, final BusinessFieldModelDao businessFieldModelDao) {
        super(businessFieldModelDao.getCreatedDate(),
              businessFieldModelDao.getCreatedBy(),
              businessFieldModelDao.getCreatedReasonCode(),
              businessFieldModelDao.getCreatedComments(),
              businessFieldModelDao.getAccountId(),
              businessFieldModelDao.getAccountName(),
              businessFieldModelDao.getAccountExternalKey(),
              businessFieldModelDao.getReportGroup());
        this.objectType = objectType;
        this.name = businessFieldModelDao.getName();
        this.value = businessFieldModelDao.getValue();
    }

    public static BusinessField create(final BusinessFieldModelDao businessFieldModelDao) {
        if (businessFieldModelDao instanceof BusinessAccountFieldModelDao) {
            return new BusinessField(ObjectType.ACCOUNT, businessFieldModelDao);
        } else if (businessFieldModelDao instanceof BusinessInvoiceFieldModelDao) {
            return new BusinessField(ObjectType.INVOICE, businessFieldModelDao);
        } else if (businessFieldModelDao instanceof BusinessInvoicePaymentFieldModelDao) {
            return new BusinessField(ObjectType.INVOICE_PAYMENT, businessFieldModelDao);
        } else {
            return null;
        }
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessField");
        sb.append("{objectType=").append(objectType);
        sb.append(", name='").append(name).append('\'');
        sb.append(", value='").append(value).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BusinessField that = (BusinessField) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result = objectType != null ? objectType.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
