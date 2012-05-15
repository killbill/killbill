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

package com.ning.billing.util.tag.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.dao.ObjectType;
import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

public class DefaultTagDefinitionUserApi implements TagUserApi {
    private TagDefinitionDao dao;

    @Inject
    public DefaultTagDefinitionUserApi(TagDefinitionDao dao) {
        this.dao = dao;
    }

    @Override
    public List<TagDefinition> getTagDefinitions() {
        return dao.getTagDefinitions();
    }

    @Override
    public TagDefinition create(final String definitionName, final String description, final CallContext context) throws TagDefinitionApiException {
        return dao.create(definitionName, description, context);
    }

    @Override
    public void deleteAllTagsForDefinition(final String definitionName, final CallContext context)
            throws TagDefinitionApiException {
        dao.deleteAllTagsForDefinition(definitionName, context);
    }

    @Override
    public void deleteTagDefinition(final String definitionName, final CallContext context) throws TagDefinitionApiException {
        dao.deleteAllTagsForDefinition(definitionName, context);
    }

	@Override
	public TagDefinition getTagDefinition(String name)
			throws TagDefinitionApiException {
		return dao.getByName(name);
	}

    @Override
    public List<Tag> createControlTags(UUID objectId, ObjectType objectType, List<TagDefinition> tagDefinitions) throws TagDefinitionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Tag createControlTag(UUID objectId, ObjectType objectType, TagDefinition tagDefinition) throws TagDefinitionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public List<Tag> createDescriptiveTags(UUID objectId, ObjectType objectType, List<TagDefinition> tagDefinitions) throws TagDefinitionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Tag createDescriptiveTag(UUID objectId, ObjectType objectType, TagDefinition tagDefinition) throws TagDefinitionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

//    @Override
//    public Tag createControlTags(String controlTagName) throws TagDefinitionApiException {
//        ControlTagType type = null;
//        for(ControlTagType t : ControlTagType.values()) {
//            if(t.toString().equals(controlTagName)) {
//                type = t;
//            }
//        }
//
//        if(type == null) {
//            throw new TagDefinitionApiException(ErrorCode.CONTROL_TAG_DOES_NOT_EXIST, controlTagName);
//        }
//        return new DefaultControlTag(type);
//    }
//
//    @Override
//    public Tag createDescriptiveTags(List) throws TagDefinitionApiException {
//        TagDefinition tagDefinition = getTagDefinition(tagDefinitionName);
//
//        return new DescriptiveTag(tagDefinition);
//    }
}
