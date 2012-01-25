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
import org.joda.time.DateTime;
import com.ning.billing.util.entity.EntityBase;

public class DescriptiveTag extends EntityBase implements Tag {
    private final String tagDefinitionName;
    private final String addedBy;
    private final DateTime addedDate;

    public DescriptiveTag(UUID id, String tagDefinitionName, String addedBy, DateTime addedDate) {
        super(id);
        this.tagDefinitionName = tagDefinitionName;
        this.addedBy = addedBy;
        this.addedDate = addedDate;
    }

    public DescriptiveTag(UUID id, TagDefinition tagDefinition, String addedBy, DateTime addedDate) {
        this(id, tagDefinition.getName(), addedBy, addedDate);
    }

    public DescriptiveTag(TagDefinition tagDefinition, String addedBy, DateTime addedDate) {
        this(UUID.randomUUID(), tagDefinition.getName(), addedBy, addedDate);
    }

    @Override
    public String getTagDefinitionName() {
        return tagDefinitionName;
    }

    @Override
    public String getAddedBy() {
        return addedBy;
    }

    @Override
    public DateTime getAddedDate() {
        return addedDate;
    }
}
