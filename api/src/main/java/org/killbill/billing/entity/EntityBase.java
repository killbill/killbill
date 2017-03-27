/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.entity;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.entity.Entity;

// Note: not suitable for Serializable Entity classes (e.g. DefaultTenant)
public abstract class EntityBase implements Entity {

    protected UUID id;
    protected DateTime createdDate;
    protected DateTime updatedDate;

    // used to hydrate objects
    public EntityBase(final UUID id) {
        this(id, null, null);
    }

    // used to create new objects
    public EntityBase() {
        this(UUIDs.randomUUID(), null, null);
    }

    public EntityBase(final UUID id, final DateTime createdDate, final DateTime updatedDate) {
        this.id = id;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public EntityBase(final Entity target) {
        this.id = target.getId();
        this.createdDate = target.getCreatedDate();
        this.updatedDate = target.getUpdatedDate();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public void setUpdatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final EntityBase that = (EntityBase) o;
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        return result;
    }

    protected <T> int safeCompareTo(final Comparable<T> c1, final T c2) {
        if (c1 == null && c2 == null) {
            return 0;
        } else if (c1 == null) {
            return -1;
        } else if (c2 == null) {
            return 1;
        } else {
            return c1.compareTo(c2);
        }
    }
}
