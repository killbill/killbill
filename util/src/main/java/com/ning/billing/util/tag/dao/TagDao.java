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

package com.ning.billing.util.tag.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.AuditedCollectionDao;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

import java.util.List;
import java.util.UUID;

public interface TagDao extends AuditedCollectionDao<Tag> {
    void insertTag(UUID objectId, ObjectType objectType, TagDefinition tagDefinition, CallContext context);

    void insertTags(UUID objectId, ObjectType objectType, List<TagDefinition> tagDefinitions, CallContext context);

    void deleteTag(UUID objectId, ObjectType objectType, TagDefinition tagDefinition, CallContext context);

//@Override
//	public List<Tag> getTagList() {
//		return tagStore.getEntityList();
//	}
//
//	@Override
//	public boolean hasTag(final TagDefinition tagDefinition) {
//		return tagStore.containsTagForDefinition(tagDefinition);
//	}
//
//    @Override
//    public boolean hasTag(ControlTagType controlTagType) {
//        return tagStore.containsTagForControlTagType(controlTagType);
//    }
//
//	@Override
//	public void addTag(final TagDefinition definition) {
//		Tag tag = new DescriptiveTag(definition);
//		tagStore.add(tag) ;
//	}
//
//    @Override
//    public void addTags(final List<Tag> tags) {
//        this.tagStore.add(tags);
//    }
//
//	@Override
//	public void addTagsFromDefinitions(final List<TagDefinition> tagDefinitions) {
//		if (tagStore != null) {
//            List<Tag> tags = new ArrayList<Tag>();
//            if (tagDefinitions != null) {
//                for (TagDefinition tagDefinition : tagDefinitions) {
//                    try {
//                        ControlTagType controlTagType = ControlTagType.valueOf(tagDefinition.getName());
//                        tags.add(new DefaultControlTag(controlTagType));
//                    } catch (IllegalArgumentException ex) {
//                        tags.add(new DescriptiveTag(tagDefinition));
//                    }
//                }
//            }
//
//			this.tagStore.add(tags);
//		}
//	}
//
//	@Override
//	public void clearTags() {
//		this.tagStore.clear();
//	}
//
//	@Override
//	public void removeTag(final TagDefinition tagDefinition) {
//		tagStore.remove(tagDefinition);
//	}
//
//	@Override
//	public boolean generateInvoice() {
//		return tagStore.generateInvoice();
//	}
//
//	@Override
//	public boolean processPayment() {
//		return tagStore.processPayment();
//	}

}
