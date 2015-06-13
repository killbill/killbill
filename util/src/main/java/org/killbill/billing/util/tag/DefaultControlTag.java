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
import org.killbill.billing.util.UUIDs;

public class DefaultControlTag extends DescriptiveTag implements ControlTag {

    private final ControlTagType controlTagType;

    // use to create new objects
    public DefaultControlTag(final ControlTagType controlTagType, final ObjectType objectType, final UUID objectId, final DateTime createdDate) {
        this(UUIDs.randomUUID(), controlTagType, objectType, objectId, createdDate);
    }

    // use to hydrate objects when loaded from the persistence layer
    public DefaultControlTag(final UUID id, final ControlTagType controlTagType, final ObjectType objectType, final UUID objectId, final DateTime createdDate) {
        super(id, controlTagType.getId(), objectType, objectId, createdDate);
        this.controlTagType = controlTagType;
    }

    @Override
    public ControlTagType getControlTagType() {
        return controlTagType;
    }

    @Override
    public String toString() {
        return "DefaultControlTag [controlTagType=" + controlTagType + ", id=" + id + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((controlTagType == null) ? 0
                                                            : controlTagType.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultControlTag other = (DefaultControlTag) obj;
        if (controlTagType != other.controlTagType) {
            return false;
        }
        return true;
    }

}
