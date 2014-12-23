/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.tenant.dao;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class TenantBroadcastModelDao extends EntityModelDaoBase implements EntityModelDao<Entity> {

    private String type;

    public TenantBroadcastModelDao() { /* For the DAO mapper */ }

    public TenantBroadcastModelDao(final String type) {
        this(UUID.randomUUID(), null, null, type);
    }

    public TenantBroadcastModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate,
                                   final String type) {
        super(id, createdDate, updatedDate);
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TenantBroadcastModelDao)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final TenantBroadcastModelDao that = (TenantBroadcastModelDao) o;

        if (type != null ? !type.equals(that.type) : that.type != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.TENANT_BROADCASTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
