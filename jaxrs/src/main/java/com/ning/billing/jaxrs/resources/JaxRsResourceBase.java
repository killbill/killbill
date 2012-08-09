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

package com.ning.billing.jaxrs.resources;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.TagJson;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public abstract class JaxRsResourceBase implements JaxrsResource {

    private static final Logger log = LoggerFactory.getLogger(JaxRsResourceBase.class);

    protected final JaxrsUriBuilder uriBuilder;
    protected final TagUserApi tagUserApi;
    protected final CustomFieldUserApi customFieldUserApi;
    protected final AuditUserApi auditUserApi;

    protected final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime();

    public JaxRsResourceBase(final JaxrsUriBuilder uriBuilder,
                             final TagUserApi tagUserApi,
                             final CustomFieldUserApi customFieldUserApi,
                             final AuditUserApi auditUserApi) {
        this.uriBuilder = uriBuilder;
        this.tagUserApi = tagUserApi;
        this.customFieldUserApi = customFieldUserApi;
        this.auditUserApi = auditUserApi;
    }

    protected abstract ObjectType getObjectType();

    public JaxRsResourceBase(final JaxrsUriBuilder uriBuilder,
                             final TagUserApi tagUserApi,
                             final CustomFieldUserApi customFieldUserApi) {
        this(uriBuilder, tagUserApi, customFieldUserApi, null);
    }

    protected Response getTags(final UUID id, final boolean withAudit) throws TagDefinitionApiException {
        final Map<String, Tag> tags = tagUserApi.getTags(id, getObjectType());
        final Collection<UUID> tagIdList = (tags.size() == 0) ?
                                           Collections.<UUID>emptyList() :
                                           Collections2.transform(tags.values(), new Function<Tag, UUID>() {
                                               @Override
                                               public UUID apply(final Tag input) {
                                                   return input.getTagDefinitionId();
                                               }
                                           });

        final AtomicReference<TagDefinitionApiException> theException = new AtomicReference<TagDefinitionApiException>();
        final List<TagDefinition> tagDefinitionList = tagUserApi.getTagDefinitions(tagIdList);
        final List<TagJson> result = ImmutableList.<TagJson>copyOf(Collections2.transform(tagIdList, new Function<UUID, TagJson>() {
            @Override
            public TagJson apply(UUID input) {
                try {
                final TagDefinition tagDefinition = findTagDefinitionFromId(tagDefinitionList, input);
                    return new TagJson(input.toString(), tagDefinition.getName(), null);
                } catch (TagDefinitionApiException e) {
                    theException.set(e);
                    return null;
                }
            }
        }));
        // Yackk..
        if (theException.get() != null) {
            throw theException.get();
        }

        return Response.status(Response.Status.OK).entity(result).build();
    }

    private TagDefinition findTagDefinitionFromId(final List<TagDefinition> tagDefinitionList, final UUID tagDefinitionId) throws TagDefinitionApiException {
        for (TagDefinition cur : tagDefinitionList) {
            if (cur.getId().equals(tagDefinitionId)) {
                return cur;
            }
        }
        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, tagDefinitionId);
    }

    protected Response createTags(final UUID id,
                                  final String tagList,
                                  final UriInfo uriInfo,
                                  final CallContext context) throws TagApiException {
        final Collection<UUID> input = getTagDefinitionUUIDs(tagList);
        tagUserApi.addTags(id, getObjectType(), input, context);
        return uriBuilder.buildResponse(this.getClass(), "getTags", id, uriInfo.getBaseUri().toString());
    }

    protected Collection<UUID> getTagDefinitionUUIDs(final String tagList) {
        final String[] tagParts = tagList.split(",\\s*");
        return Collections2.transform(ImmutableList.copyOf(tagParts), new Function<String, UUID>() {
            @Override
            public UUID apply(final String input) {
                return UUID.fromString(input);
            }
        });
    }

    protected Response deleteTags(final UUID id,
                                  final String tagList,
                                  final CallContext context) throws TagApiException {
        final Collection<UUID> input = getTagDefinitionUUIDs(tagList);
        tagUserApi.removeTags(id, getObjectType(), input, context);

        return Response.status(Response.Status.OK).build();
    }

    protected Response getCustomFields(final UUID id) {
        final Map<String, CustomField> fields = customFieldUserApi.getCustomFields(id, getObjectType());

        final List<CustomFieldJson> result = new LinkedList<CustomFieldJson>();
        for (final CustomField cur : fields.values()) {
            result.add(new CustomFieldJson(cur));
        }

        return Response.status(Response.Status.OK).entity(result).build();
    }

    protected Response createCustomFields(final UUID id,
                                          final List<CustomFieldJson> customFields,
                                          final CallContext context) {
        final LinkedList<CustomField> input = new LinkedList<CustomField>();
        for (final CustomFieldJson cur : customFields) {
            input.add(new StringCustomField(cur.getName(), cur.getValue()));
        }

        customFieldUserApi.saveCustomFields(id, getObjectType(), input, context);
        return uriBuilder.buildResponse(this.getClass(), "createCustomFields", id);
    }

    protected Response deleteCustomFields(final UUID id,
                                          final String customFieldList,
                                          final CallContext context) {
        // STEPH missing API to delete custom fields
        return Response.status(Response.Status.OK).build();
    }
}
