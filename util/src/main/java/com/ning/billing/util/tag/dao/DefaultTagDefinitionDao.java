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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.events.TagDefinitionInternalEvent;
import com.ning.billing.util.svcsapi.bus.Bus;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

public class DefaultTagDefinitionDao implements TagDefinitionDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultTagDefinitionDao.class);

    private final TagDefinitionSqlDao tagDefinitionSqlDao;
    private final TagEventBuilder tagEventBuilder;
    private final Bus bus;

    @Inject
    public DefaultTagDefinitionDao(final IDBI dbi, final TagEventBuilder tagEventBuilder, final Bus bus) {
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
        this.tagDefinitionSqlDao = dbi.onDemand(TagDefinitionSqlDao.class);
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final InternalTenantContext context) {
        // Get user definitions from the database
        final List<TagDefinition> definitionList = new LinkedList<TagDefinition>();
        definitionList.addAll(tagDefinitionSqlDao.get(context));

        // Add control tag definitions
        for (final ControlTagType controlTag : ControlTagType.values()) {
            definitionList.add(new DefaultTagDefinition(controlTag));
        }
        return definitionList;
    }

    @Override
    public TagDefinition getByName(final String definitionName, final InternalTenantContext context) {
        for (final ControlTagType controlTag : ControlTagType.values()) {
            if (controlTag.name().equals(definitionName)) {
                return new DefaultTagDefinition(controlTag);
            }
        }
        return tagDefinitionSqlDao.getByName(definitionName, context);
    }

    @Override
    public TagDefinition getById(final UUID definitionId, final InternalTenantContext context) {
        for (final ControlTagType controlTag : ControlTagType.values()) {
            if (controlTag.getId().equals(definitionId)) {
                return new DefaultTagDefinition(controlTag);
            }
        }
        return tagDefinitionSqlDao.getById(definitionId.toString(), context);
    }

    @Override
    public List<TagDefinition> getByIds(final Collection<UUID> definitionIds, final InternalTenantContext context) {
        final List<TagDefinition> result = new LinkedList<TagDefinition>();
        for (final UUID cur : definitionIds) {
            for (final ControlTagType controlTag : ControlTagType.values()) {
                if (controlTag.getId().equals(cur)) {
                    result.add(new DefaultTagDefinition(controlTag));
                    break;
                }
            }
        }
        if (definitionIds.size() > 0) {
            result.addAll(tagDefinitionSqlDao.getByIds(Collections2.transform(definitionIds, new Function<UUID, String>() {
                @Override
                public String apply(final UUID input) {
                    return input.toString();
                }

            }), context));
        }
        return result;
    }

    @Override
    public TagDefinition create(final String definitionName, final String description,
                                final InternalCallContext context) throws TagDefinitionApiException {
        // Make sure a control tag with this name don't already exist
        if (isControlTagName(definitionName)) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_CONFLICTS_WITH_CONTROL_TAG, definitionName);
        }

        try {
            return tagDefinitionSqlDao.inTransaction(new Transaction<TagDefinition, TagDefinitionSqlDao>() {
                @Override
                public TagDefinition inTransaction(final TagDefinitionSqlDao tagDefinitionSqlDao, final TransactionStatus status) throws Exception {
                    // Make sure the tag definition doesn't exist already
                    final TagDefinition existingDefinition = tagDefinitionSqlDao.getByName(definitionName, context);
                    if (existingDefinition != null) {
                        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_ALREADY_EXISTS, definitionName);
                    }

                    // Create it
                    final TagDefinition tagDefinition = new DefaultTagDefinition(definitionName, description, false);
                    tagDefinitionSqlDao.create(tagDefinition, context);

                    // Post an event to the bus
                    final TagDefinitionInternalEvent tagDefinitionEvent;
                    if (tagDefinition.isControlTag()) {
                        tagDefinitionEvent = tagEventBuilder.newControlTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition, context);
                    } else {
                        tagDefinitionEvent = tagEventBuilder.newUserTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition, context);
                    }
                    try {
                        bus.postFromTransaction(tagDefinitionEvent, tagDefinitionSqlDao, context);
                    } catch (Bus.EventBusException e) {
                        log.warn("Failed to post tag definition creation event for tag " + tagDefinition.getId(), e);
                    }

                    return tagDefinition;
                }
            });
        } catch (TransactionFailedException exception) {
            if (exception.getCause() instanceof TagDefinitionApiException) {
                throw (TagDefinitionApiException) exception.getCause();
            } else {
                throw exception;
            }
        }
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
    public void deleteById(final UUID definitionId, final InternalCallContext context) throws TagDefinitionApiException {
        try {
            tagDefinitionSqlDao.inTransaction(new Transaction<Void, TagDefinitionSqlDao>() {
                @Override
                public Void inTransaction(final TagDefinitionSqlDao tagDefinitionSqlDao, final TransactionStatus status) throws Exception {
                    // Make sure the tag definition exists
                    final TagDefinition tagDefinition = tagDefinitionSqlDao.getById(definitionId.toString(), context);
                    if (tagDefinition == null) {
                        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionId);
                    }

                    // Make sure it is not used currently
                    if (tagDefinitionSqlDao.tagDefinitionUsageCount(definitionId.toString(), context) > 0) {
                        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_IN_USE, definitionId);
                    }

                    // Delete it
                    tagDefinitionSqlDao.deleteTagDefinition(definitionId.toString(), context);

                    // Post an event to the Bus
                    final TagDefinitionInternalEvent tagDefinitionEvent;
                    if (tagDefinition.isControlTag()) {
                        tagDefinitionEvent = tagEventBuilder.newControlTagDefinitionDeletionEvent(tagDefinition.getId(), tagDefinition, context);
                    } else {
                        tagDefinitionEvent = tagEventBuilder.newUserTagDefinitionDeletionEvent(tagDefinition.getId(), tagDefinition, context);
                    }
                    try {
                        bus.postFromTransaction(tagDefinitionEvent, tagDefinitionSqlDao, context);
                    } catch (Bus.EventBusException e) {
                        log.warn("Failed to post tag definition deletion event for tag " + tagDefinition.getId(), e);
                    }

                    return null;
                }
            });
        } catch (TransactionFailedException exception) {
            if (exception.getCause() instanceof TagDefinitionApiException) {
                throw (TagDefinitionApiException) exception.getCause();
            } else {
                throw exception;
            }
        }
    }
}
