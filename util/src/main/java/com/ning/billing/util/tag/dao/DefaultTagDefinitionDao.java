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
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

public class DefaultTagDefinitionDao implements TagDefinitionDao {
    private final TagDefinitionSqlDao dao;
    private final TagEventBuilder tagEventBuilder;
    private final Bus bus;

    @Inject
    public DefaultTagDefinitionDao(final IDBI dbi, final TagEventBuilder tagEventBuilder, final Bus bus) {
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
        this.dao = dbi.onDemand(TagDefinitionSqlDao.class);
    }

    @Override
    public List<TagDefinition> getTagDefinitions() {
        // get user definitions from the database
        final List<TagDefinition> definitionList = new ArrayList<TagDefinition>();
        definitionList.addAll(dao.get());

        // add control tag definitions
        for (final ControlTagType controlTag : ControlTagType.values()) {
            definitionList.add(new DefaultTagDefinition(controlTag.toString(), controlTag.getDescription(), true));
        }

        return definitionList;
    }

    @Override
    public TagDefinition getByName(final String definitionName) {
        // add control tag definitions
        for (final ControlTagType controlTag : ControlTagType.values()) {
            if (definitionName.equals(controlTag.name())) {
                return new DefaultTagDefinition(controlTag.toString(), controlTag.getDescription(), true);
            }
        }
        return dao.getByName(definitionName);
    }

    @Override
    public TagDefinition create(final String definitionName, final String description,
                                final CallContext context) throws TagDefinitionApiException {
        if (isControlTagName(definitionName)) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_CONFLICTS_WITH_CONTROL_TAG, definitionName);
        }

        final TagDefinition existingDefinition = dao.getByName(definitionName);

        if (existingDefinition != null) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_ALREADY_EXISTS, definitionName);
        }

        final TagDefinition definition = new DefaultTagDefinition(definitionName, description, false);
        dao.create(definition, context);
        return definition;
    }

    private boolean isControlTagName(final String definitionName) {
        for (final ControlTagType controlTagName : ControlTagType.values()) {
            if (controlTagName.toString().equals(definitionName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void deleteAllTagsForDefinition(final String definitionName, final CallContext context) throws TagDefinitionApiException {
        final TagDefinition existingDefinition = dao.getByName(definitionName);
        if (existingDefinition == null) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionName);
        }

        dao.deleteAllTagsForDefinition(definitionName, context);
    }

    @Override
    public void deleteTagDefinition(final String definitionName, final CallContext context) throws TagDefinitionApiException {
        if (dao.tagDefinitionUsageCount(definitionName) > 0) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_IN_USE, definitionName);
        }

        final TagDefinition existingDefinition = dao.getByName(definitionName);

        if (existingDefinition == null) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionName);
        }

        dao.deleteTagDefinition(definitionName, context);
    }
}
