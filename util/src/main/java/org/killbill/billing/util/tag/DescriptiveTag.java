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

package org.killbill.billing.util.tag;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.ObjectType;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.UUIDs;

public class DescriptiveTag extends EntityBase implements Tag {

    private final UUID tagDefinitionId;
    private final UUID objectId;
    private final ObjectType objectType;

    // use to hydrate objects from the persistence layer
    public DescriptiveTag(final UUID id, final UUID tagDefinitionId, final ObjectType objectType, final UUID objectId, final DateTime createdDate) {
        super(id, createdDate, createdDate);
        this.tagDefinitionId = tagDefinitionId;
        this.objectType = objectType;
        this.objectId = objectId;
    }

    // use to create new objects
    public DescriptiveTag(final UUID tagDefinitionId, final ObjectType objectType, final UUID objectId, final DateTime createdDate) {
        super(UUIDs.randomUUID(), createdDate, createdDate);
        this.tagDefinitionId = tagDefinitionId;
        this.objectType = objectType;
        this.objectId = objectId;
    }

    @Override
    public UUID getTagDefinitionId() {
        return tagDefinitionId;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public String toString() {
        return "DescriptiveTag [tagDefinitionId=" + tagDefinitionId + ", id=" + id + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((tagDefinitionId == null) ? 0
                                                             : tagDefinitionId.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DescriptiveTag other = (DescriptiveTag) obj;
        if (tagDefinitionId == null) {
            if (other.tagDefinitionId != null) {
                return false;
            }
        } else if (!tagDefinitionId.equals(other.tagDefinitionId)) {
            return false;
        }
        return true;
    }
}
