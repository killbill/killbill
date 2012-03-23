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

import java.util.ArrayList;
import java.util.List;
import org.skife.jdbi.v2.IDBI;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public class DefaultTagDefinitionDao implements TagDefinitionDao {
    private final TagDefinitionSqlDao dao;
    private final Clock clock;

    @Inject
    public DefaultTagDefinitionDao(IDBI dbi, Clock clock) {
        this.dao = dbi.onDemand(TagDefinitionSqlDao.class);
        this.clock = clock;
    }

    @Override
    public List<TagDefinition> getTagDefinitions() {
        // get user definitions from the database
        List<TagDefinition> definitionList = new ArrayList<TagDefinition>();
        definitionList.addAll(dao.get());

        // add control tag definitions
        for (ControlTagType controlTag : ControlTagType.values()) {
            definitionList.add(new DefaultTagDefinition(controlTag.toString(), controlTag.getDescription(), null));
        }

        return definitionList;
    }

    @Override
    public TagDefinition getByName(final String definitionName) {
        return dao.getByName(definitionName);
    }

    @Override
    public TagDefinition create(final String definitionName, final String description, final String createdBy) throws TagDefinitionApiException {
        if (isControlTagName(definitionName)) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_CONFLICTS_WITH_CONTROL_TAG, definitionName);
        }

        TagDefinition existingDefinition = dao.getByName(definitionName);

        if (existingDefinition != null) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_ALREADY_EXISTS, definitionName);
        }

        TagDefinition definition = new DefaultTagDefinition(definitionName, description, createdBy);
        dao.create(definition);
        return definition;
    }

    private boolean isControlTagName(final String definitionName) {
        for (ControlTagType controlTagName : ControlTagType.values()) {
            if (controlTagName.toString().equals(definitionName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void deleteAllTagsForDefinition(final String definitionName) throws TagDefinitionApiException {
        TagDefinition existingDefinition = dao.getByName(definitionName);
        if (existingDefinition == null) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionName);
        }

        dao.deleteAllTagsForDefinition(definitionName);
    }

    @Override
    public void deleteTagDefinition(final String definitionName) throws TagDefinitionApiException {
        if (dao.tagDefinitionUsageCount(definitionName) > 0) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_IN_USE, definitionName);
        }

        TagDefinition existingDefinition = dao.getByName(definitionName);

        if (existingDefinition == null) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionName);
        }

        dao.deleteTagDefinition(definitionName);
    }
}
