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

package com.ning.billing.entitlement.engine.dao.model;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class SubscriptionBundleModelDao extends EntityBase implements EntityModelDao<SubscriptionBundle> {

    private String externalKey;
    private UUID accountId;
    private DateTime lastSysUpdateDate;

    public SubscriptionBundleModelDao() { /* For the DAO mapper */ }

    public SubscriptionBundleModelDao(final UUID id, final String key, final UUID accountId, final DateTime lastSysUpdateDate,
                                      final DateTime createdDate, final DateTime updateDate) {
        super(id, createdDate, updateDate);
        this.externalKey = key;
        this.accountId = accountId;
        this.lastSysUpdateDate = lastSysUpdateDate;
    }

    public SubscriptionBundleModelDao(final SubscriptionBundleData input) {
        this(input.getId(), input.getExternalKey(), input.getAccountId(), input.getLastSysUpdateDate(), input.getCreatedDate(), input.getUpdatedDate());
    }

    public String getExternalKey() {
        return externalKey;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public DateTime getLastSysUpdateDate() {
        return lastSysUpdateDate;
    }

    public static SubscriptionBundle toSubscriptionbundle(final SubscriptionBundleModelDao src) {
        if (src == null) {
            return null;
        }
        return new SubscriptionBundleData(src.getId(), src.getExternalKey(), src.getAccountId(), src.getLastSysUpdateDate());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SubscriptionBundleModelDao");
        sb.append("{externalKey='").append(externalKey).append('\'');
        sb.append(", accountId=").append(accountId);
        sb.append(", lastSysUpdateDate=").append(lastSysUpdateDate);
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

        final SubscriptionBundleModelDao that = (SubscriptionBundleModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (lastSysUpdateDate != null ? !lastSysUpdateDate.equals(that.lastSysUpdateDate) : that.lastSysUpdateDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (lastSysUpdateDate != null ? lastSysUpdateDate.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.BUNDLES;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }

}
