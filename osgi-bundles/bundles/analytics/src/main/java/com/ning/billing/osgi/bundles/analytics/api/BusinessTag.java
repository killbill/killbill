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
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessBundleTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceTagModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessTagModelDao;

public class BusinessTag extends BusinessEntityBase {

    private final ObjectType objectType;
    private final String name;

    private BusinessTag(final ObjectType objectType, final BusinessTagModelDao businessTagModelDao) {
        super(businessTagModelDao.getCreatedDate(),
              businessTagModelDao.getCreatedBy(),
              businessTagModelDao.getCreatedReasonCode(),
              businessTagModelDao.getCreatedComments(),
              businessTagModelDao.getAccountId(),
              businessTagModelDao.getAccountName(),
              businessTagModelDao.getAccountExternalKey(),
              businessTagModelDao.getReportGroup());
        this.objectType = objectType;
        this.name = businessTagModelDao.getName();
    }

    public static BusinessTag create(final BusinessTagModelDao businessTagModelDao) {
        if (businessTagModelDao instanceof BusinessAccountTagModelDao) {
            return new BusinessTag(ObjectType.ACCOUNT, businessTagModelDao);
        } else if (businessTagModelDao instanceof BusinessBundleTagModelDao) {
            return new BusinessTag(ObjectType.BUNDLE, businessTagModelDao);
        } else if (businessTagModelDao instanceof BusinessInvoiceTagModelDao) {
            return new BusinessTag(ObjectType.INVOICE, businessTagModelDao);
        } else if (businessTagModelDao instanceof BusinessInvoicePaymentTagModelDao) {
            return new BusinessTag(ObjectType.INVOICE_PAYMENT, businessTagModelDao);
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessTag");
        sb.append("{objectType=").append(objectType);
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

        final BusinessTag that = (BusinessTag) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = objectType != null ? objectType.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
