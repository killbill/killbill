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
import com.ning.billing.analytics.model.BusinessAccountFieldModelDao;
import com.ning.billing.analytics.model.BusinessFieldModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceFieldModelDao;
import com.ning.billing.analytics.model.BusinessInvoicePaymentFieldModelDao;
import com.ning.billing.analytics.model.BusinessSubscriptionTransitionFieldModelDao;
import com.ning.billing.util.entity.EntityBase;

public class DefaultBusinessField extends EntityBase implements BusinessField {

    private final ObjectType objectType;
    private final String name;
    private final String value;

    DefaultBusinessField(final ObjectType objectType, final BusinessFieldModelDao businessFieldModelDao) {
        super(businessFieldModelDao.getId());
        this.objectType = objectType;
        this.name = businessFieldModelDao.getName();
        this.value = businessFieldModelDao.getValue();
    }

    public DefaultBusinessField(final BusinessAccountFieldModelDao businessAccountFieldModelDao) {
        this(ObjectType.ACCOUNT, businessAccountFieldModelDao);
    }

    public DefaultBusinessField(final BusinessInvoiceFieldModelDao businessInvoiceFieldModelDao) {
        this(ObjectType.INVOICE, businessInvoiceFieldModelDao);
    }

    public DefaultBusinessField(final BusinessInvoicePaymentFieldModelDao businessInvoicePaymentFieldModelDao) {
        this(ObjectType.PAYMENT, businessInvoicePaymentFieldModelDao);
    }

    public DefaultBusinessField(final BusinessSubscriptionTransitionFieldModelDao businessSubscriptionTransitionFieldModelDao) {
        this(ObjectType.BUNDLE, businessSubscriptionTransitionFieldModelDao);
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
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBusinessField");
        sb.append("{objectType=").append(objectType);
        sb.append(", name='").append(name).append('\'');
        sb.append(", value='").append(value).append('\'');
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

        final DefaultBusinessField that = (DefaultBusinessField) o;

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

    @Override
    public int hashCode() {
        int result = objectType != null ? objectType.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
