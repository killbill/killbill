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

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
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
    public TagDefinition create(final String name, final String description, final String createdBy) throws TagDefinitionApiException {
        return dao.create(name, description, createdBy);
    }

    @Override
    public void deleteAllTagsForDefinition(final String definitionName) throws TagDefinitionApiException {
        dao.deleteAllTagsForDefinition(definitionName);
    }

    @Override
    public void deleteTagDefinition(final String definitionName) throws TagDefinitionApiException {
        dao.deleteAllTagsForDefinition(definitionName);
    }

	@Override
	public TagDefinition getTagDefinition(String name)
			throws TagDefinitionApiException {
		return dao.getByName(name);
	}

    @Override
    public Tag createControlTag(String controlTagName, String addedBy,
            DateTime addedDate) throws TagDefinitionApiException {
        ControlTagType type = null;
        for(ControlTagType t : ControlTagType.values()) {
            if(t.toString().equals(controlTagName)) {
                type = t;
            }
        }
        
        if(type == null) {
            throw new TagDefinitionApiException(ErrorCode.CONTROL_TAG_DOES_NOT_EXIST, controlTagName);
        }
        return new DefaultControlTag(addedBy, addedDate, type);
    }

    @Override
    public Tag createDescriptiveTag(String tagDefinitionName, String addedBy,
            DateTime addedDate) throws TagDefinitionApiException {
        TagDefinition tagDefinition = getTagDefinition(tagDefinitionName);
        
        return new DescriptiveTag(tagDefinition, addedBy, addedDate);
    }
}
