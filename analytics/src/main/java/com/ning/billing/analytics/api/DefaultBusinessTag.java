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

package com.ning.billing.analytics.api;

import com.ning.billing.ObjectType;
import com.ning.billing.analytics.model.BusinessAccountTagModelDao;
import com.ning.billing.analytics.model.BusinessInvoicePaymentTagModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceTagModelDao;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionTagModelDao;
import com.ning.billing.analytics.model.BusinessTagModelDao;
import com.ning.billing.util.entity.EntityBase;

public class DefaultBusinessTag extends EntityBase implements BusinessTag {

    private final ObjectType objectType;
    private final String name;

    DefaultBusinessTag(final ObjectType objectType, final BusinessTagModelDao businessTagModelDao) {
        super(businessTagModelDao.getId());
        this.objectType = objectType;
        this.name = businessTagModelDao.getName();
    }

    public DefaultBusinessTag(final BusinessAccountTagModelDao businessAccountTagModelDao) {
        this(ObjectType.ACCOUNT, businessAccountTagModelDao);
    }

    public DefaultBusinessTag(final BusinessInvoiceTagModelDao businessInvoiceTagModelDao) {
        this(ObjectType.INVOICE, businessInvoiceTagModelDao);
    }

    public DefaultBusinessTag(final BusinessInvoicePaymentTagModelDao businessInvoicePaymentTagModelDao) {
        this(ObjectType.PAYMENT, businessInvoicePaymentTagModelDao);
    }

    public DefaultBusinessTag(final BusinessSubscriptionTransitionTagModelDao businessSubscriptionTransitionTagModelDao) {
        this(ObjectType.BUNDLE, businessSubscriptionTransitionTagModelDao);
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBusinessTag");
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

        final DefaultBusinessTag that = (DefaultBusinessTag) o;

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
