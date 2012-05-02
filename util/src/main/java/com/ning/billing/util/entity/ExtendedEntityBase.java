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

package com.ning.billing.util.entity;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.Customizable;
import com.ning.billing.util.customfield.DefaultFieldStore;
import com.ning.billing.util.customfield.FieldStore;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DefaultTagStore;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.TagStore;
import com.ning.billing.util.tag.Taggable;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class ExtendedEntityBase extends EntityBase implements Customizable, Taggable {
    protected final FieldStore fields;
    protected final TagStore tagStore;

    public ExtendedEntityBase() {
        super();
        this.fields = DefaultFieldStore.create(getId(), getObjectName());
        this.tagStore = new DefaultTagStore(id, getObjectName());
    }

    public ExtendedEntityBase(final UUID id, @Nullable final String createdBy, @Nullable final DateTime createdDate) {
        super(id, createdBy, createdDate);
        this.fields = DefaultFieldStore.create(getId(), getObjectName());
        this.tagStore = new DefaultTagStore(id, getObjectName());
    }

    @Override
    public String getFieldValue(final String fieldName) {
        return fields.getValue(fieldName);
    }

    @Override
    public void setFieldValue(final String fieldName, final String fieldValue) {
        fields.setValue(fieldName, fieldValue);
    }

    @Override
    public List<CustomField> getFieldList() {
        return fields.getEntityList();
    }

    @Override
    public void setFields(final List<CustomField> fields) {
        if (fields != null) {
            this.fields.add(fields);
        }
    }

    @Override
    public void clearFields() {
        fields.clear();
    }

    @Override
	public List<Tag> getTagList() {
		return tagStore.getEntityList();
	}

	@Override
	public boolean hasTag(final TagDefinition definition) {
		return tagStore.containsTagForDefinition(definition);
	}

    @Override
    public boolean hasTag(ControlTagType controlTagType) {
        return tagStore.containsTagForControlTagType(controlTagType);
    }

	@Override
	public void addTag(final TagDefinition definition) {
		Tag tag = new DescriptiveTag(definition);
		tagStore.add(tag) ;
	}

    @Override
    public void addTags(final List<Tag> tags) {
        this.tagStore.add(tags);
    }

	@Override
	public void addTagsFromDefinitions(final List<TagDefinition> tagDefinitions) {
		if (tagStore != null) {
            List<Tag> tags = new ArrayList<Tag>();
            if (tagDefinitions != null) {
                for (TagDefinition tagDefinition : tagDefinitions) {
                    try {
                        ControlTagType controlTagType = ControlTagType.valueOf(tagDefinition.getName());
                        tags.add(new DefaultControlTag(controlTagType));
                    } catch (IllegalArgumentException ex) {
                        tags.add(new DescriptiveTag(tagDefinition));
                    }
                }
            }

			this.tagStore.add(tags);
		}
	}

	@Override
	public void clearTags() {
		this.tagStore.clear();
	}

	@Override
	public void removeTag(final TagDefinition tagDefinition) {
		tagStore.remove(tagDefinition);
	}

	@Override
	public boolean generateInvoice() {
		return tagStore.generateInvoice();
	}

	@Override
	public boolean processPayment() {
		return tagStore.processPayment();
	}

    @Override
    public abstract String getObjectName();

    @Override
    public abstract void saveFieldValue(String fieldName, String fieldValue, CallContext context);

    @Override
    public abstract void saveFields(List<CustomField> fields, CallContext context);

    @Override
    public abstract void clearPersistedFields(CallContext context);
}
