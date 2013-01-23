/* 
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class PaymentMethodModelDao extends EntityBase implements EntityModelDao<PaymentMethod> {

    private UUID accountId;
    private String pluginName;
    private Boolean isActive;
    private String externalId;

    public PaymentMethodModelDao() { /* For the DAO mapper */ }

    public PaymentMethodModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                 final UUID accountId, final String pluginName,
                                 final Boolean isActive, final String externalId) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.pluginName = pluginName;
        this.isActive = isActive;
        this.externalId = externalId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getPluginName() {
        return pluginName;
    }

    // TODO  Required for making the BindBeanFactory with Introspector work
    public Boolean getIsActive() {
        return isActive;
    }

    public Boolean isActive() {
        return isActive;
    }

    public String getExternalId() {
        return externalId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PaymentMethodModelDao");
        sb.append("{accountId=").append(accountId);
        sb.append(", pluginName='").append(pluginName).append('\'');
        sb.append(", isActive=").append(isActive);
        sb.append(", externalId='").append(externalId).append('\'');
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

        final PaymentMethodModelDao that = (PaymentMethodModelDao) o;

        if (!equalsButActive(that)) {
            return false;
        }

        if (isActive != null ? !isActive.equals(that.isActive) : that.isActive != null) {
            return false;
        }

        return true;
    }

    public boolean equalsButActive(final PaymentMethodModelDao that) {
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (externalId != null ? !externalId.equals(that.externalId) : that.externalId != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
        result = 31 * result + (externalId != null ? externalId.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.PAYMENT_METHODS;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.PAYMENT_METHOD_HISTORY;
    }

}
