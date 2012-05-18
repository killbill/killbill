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

package com.ning.billing.util.tag;

import java.util.UUID;

public class DefaultControlTag extends DescriptiveTag implements ControlTag {
    private final ControlTagType controlTagType;

    // use to create new objects
    public DefaultControlTag(final ControlTagType controlTagType) {
        super(controlTagType.toString());
        this.controlTagType = controlTagType;
    }

    // use to hydrate objects when loaded from the persistence layer
    public DefaultControlTag(final UUID id, final ControlTagType controlTagType) {
        super(id, controlTagType.toString());
        this.controlTagType = controlTagType;
    }

    @Override
    public ControlTagType getControlTagType() {
        return controlTagType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultControlTag that = (DefaultControlTag) o;

        if (controlTagType != that.controlTagType) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return controlTagType != null ? controlTagType.hashCode() : 0;
    }
}
