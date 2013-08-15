/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.TagJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;
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
    protected final AccountUserApi accountUserApi;
    protected final Context context;

    protected final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTimeParser();

    public JaxRsResourceBase(final JaxrsUriBuilder uriBuilder,
                             final TagUserApi tagUserApi,
                             final CustomFieldUserApi customFieldUserApi,
                             final AuditUserApi auditUserApi,
                             final AccountUserApi accountUserApi,
                             final Context context) {
        this.uriBuilder = uriBuilder;
        this.tagUserApi = tagUserApi;
        this.customFieldUserApi = customFieldUserApi;
        this.auditUserApi = auditUserApi;
        this.accountUserApi = accountUserApi;
        this.context = context;
    }

    protected ObjectType getObjectType() {
        return null;
    }

    protected Response getTags(final UUID id, final boolean withAudit, final TenantContext context) throws TagDefinitionApiException {
        final List<Tag> tags = tagUserApi.getTagsForObject(id, getObjectType(), context);
        final Collection<UUID> tagIdList = (tags.size() == 0) ?
                                           Collections.<UUID>emptyList() :
                                           Collections2.transform(tags, new Function<Tag, UUID>() {
                                               @Override
                                               public UUID apply(final Tag input) {
                                                   return input.getTagDefinitionId();
                                               }
                                           });

        final AtomicReference<TagDefinitionApiException> theException = new AtomicReference<TagDefinitionApiException>();
        final List<TagDefinition> tagDefinitionList = tagUserApi.getTagDefinitions(tagIdList, context);
        final List<TagJson> result = ImmutableList.<TagJson>copyOf(Collections2.transform(tagIdList, new Function<UUID, TagJson>() {
            @Override
            public TagJson apply(final UUID input) {
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
        // TODO This will always return 201, even if some (or all) tags already existed (in which case we don't do anything)
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

    protected Response getCustomFields(final UUID id, final TenantContext context) {
        final List<CustomField> fields = customFieldUserApi.getCustomFieldsForObject(id, getObjectType(), context);

        final List<CustomFieldJson> result = new LinkedList<CustomFieldJson>();
        for (final CustomField cur : fields) {
            result.add(new CustomFieldJson(cur));
        }

        return Response.status(Response.Status.OK).entity(result).build();
    }

    protected Response createCustomFields(final UUID id,
                                          final List<CustomFieldJson> customFields,
                                          final CallContext context) throws CustomFieldApiException {
        final LinkedList<CustomField> input = new LinkedList<CustomField>();
        for (final CustomFieldJson cur : customFields) {
            input.add(new StringCustomField(cur.getName(), cur.getValue(), getObjectType(), id, context.getCreatedDate()));
        }

        customFieldUserApi.addCustomFields(input, context);
        return uriBuilder.buildResponse(this.getClass(), "createCustomFields", id);
    }

    protected Response deleteCustomFields(final UUID id,
                                          final String customFieldList,
                                          final CallContext context) {
        // STEPH missing API to delete custom fields
        return Response.status(Response.Status.OK).build();
    }

    protected LocalDate toLocalDate(final UUID accountId, final DateTime inputDate, final TenantContext context)  {
        Account account = null;
        try {
            account = accountId != null ? accountUserApi.getAccountById(accountId, context) : null;
        } catch (AccountApiException e) {
            log.info("Failed to retrieve account for id " + accountId );
        }

        if (account == null && inputDate == null) {
            // We have no inputDate and so accountTimeZone so we default to LocalDate as seen in UTC
            return new LocalDate();
        } else if (account == null && inputDate != null) {
            // We were given a date but can't get timezone, default in UTC
            return new LocalDate(inputDate);
        } else if (account != null && inputDate == null) {
            // We have no inputDate but for accountTimeZone so default to LocalDate as seen in account timezone
            return new LocalDate(account.getTimeZone());
        } else {
            // Precise LocalDate as requested
            return new LocalDate(inputDate, account.getTimeZone());
        }
    }
}
