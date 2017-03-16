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

package org.killbill.billing.payment.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

import com.google.common.base.MoreObjects;

public class PaymentMethodModelDao extends EntityModelDaoBase implements EntityModelDao<PaymentMethod> {

    private String externalKey;
    private UUID accountId;
    private String pluginName;
    private Boolean isActive;

    public PaymentMethodModelDao() { /* For the DAO mapper */ }

    public PaymentMethodModelDao(final UUID id,  @Nullable final String externalKey, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                 final UUID accountId, final String pluginName,
                                 final Boolean isActive) {
        super(id, createdDate, updatedDate);
        this.externalKey = MoreObjects.firstNonNull(externalKey, id.toString());
        this.accountId = accountId;
        this.pluginName = pluginName;
        this.isActive = isActive;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(final String externalKey) {
        this.externalKey = externalKey;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setAccountId(final UUID accountId) {
        this.accountId = accountId;
    }

    public void setPluginName(final String pluginName) {
        this.pluginName = pluginName;
    }

    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    // TODO  Required for making the BindBeanFactory with Introspector work
    public Boolean getIsActive() {
        return isActive;
    }

    public Boolean isActive() {
        return isActive;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PaymentMethodModelDao");
        sb.append("{accountId=").append(accountId);
        sb.append(", pluginName='").append(pluginName).append('\'');
        sb.append(", isActive=").append(isActive);
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
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        /*
        TODO unclear
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        */
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
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
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        result = 31 * result + (isActive != null ? isActive.hashCode() : 0);
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
