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
import com.ning.billing.util.entity.EntityBase;

public class SubscriptionBundleModelDao extends EntityBase {

    private final String key;
    private final UUID accountId;
    private final DateTime lastSysUpdateDate;

    public SubscriptionBundleModelDao(final UUID id, final String key, final UUID accountId, final DateTime lastSysUpdateDate, final DateTime createdDate, final DateTime updateDate) {
        super(id, createdDate, updateDate);
        this.key = key;
        this.accountId = accountId;
        this.lastSysUpdateDate = lastSysUpdateDate;
    }


    public SubscriptionBundleModelDao(SubscriptionBundleData input) {
        this(input.getId(), input.getExternalKey(), input.getAccountId(), input.getLastSysUpdateDate(), input.getCreatedDate(), input.getUpdatedDate());
    }

    public String getKey() {
        return key;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public DateTime getLastSysUpdateDate() {
        return lastSysUpdateDate;
    }

    public static SubscriptionBundle toSubscriptionbundle(SubscriptionBundleModelDao src) {
        if (src == null) {
            return null;
        }
        return new SubscriptionBundleData(src.getId(), src.getKey(), src.getAccountId(), src.getLastSysUpdateDate());
    }
}
