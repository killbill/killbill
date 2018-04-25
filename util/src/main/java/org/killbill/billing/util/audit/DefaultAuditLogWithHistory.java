/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.audit;

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.audit.dao.AuditLogModelDao;
import org.killbill.billing.util.entity.Entity;

public class DefaultAuditLogWithHistory<E extends Entity> extends DefaultAuditLog implements AuditLogWithHistory<E> {

    private E entity;

    public DefaultAuditLogWithHistory(final E entity,final AuditLogModelDao auditLogModelDao, final ObjectType objectType, final UUID auditedEntityId) {
        super(auditLogModelDao, objectType, auditedEntityId);
        this.entity = entity;
    }

    @Override
    public E getEntity() {
        return this.entity;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultAuditLogWithHistory{");
        sb.append("auditLog=").append(super.toString());
        sb.append(", entity=").append(entity);
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

        final DefaultAuditLogWithHistory<E> that = (DefaultAuditLogWithHistory<E>) o;

        if (entity != null ? !entity.equals(that.entity) : that.entity != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (entity != null ? entity.hashCode() : 0);
        return result;
    }
}
