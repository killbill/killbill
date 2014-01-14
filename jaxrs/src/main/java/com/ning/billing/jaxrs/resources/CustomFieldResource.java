/*
 * Copyright 2010-2014 Ning, Inc.
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

import java.net.URI;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.Clock;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.entity.Pagination;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CUSTOM_FIELDS_PATH)
public class CustomFieldResource extends JaxRsResourceBase {

    @Inject
    public CustomFieldResource(final JaxrsUriBuilder uriBuilder,
                               final TagUserApi tagUserApi,
                               final CustomFieldUserApi customFieldUserApi,
                               final AuditUserApi auditUserApi,
                               final AccountUserApi accountUserApi,
                               final Clock clock,
                               final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
    }

    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                    @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Pagination<CustomField> customFields = customFieldUserApi.getCustomFields(offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(CustomFieldResource.class, "getCustomFields", customFields.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_AUDIT, auditMode.toString()));

        return buildStreamingPaginationResponse(customFields,
                                                new Function<CustomField, CustomFieldJson>() {
                                                    @Override
                                                    public CustomFieldJson apply(final CustomField customField) {
                                                        // TODO Really slow - we should instead try to figure out the account id
                                                        final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(customField.getId(), ObjectType.CUSTOM_FIELD, auditMode.getLevel(), tenantContext);
                                                        return new CustomFieldJson(customField, auditLogs);
                                                    }
                                                },
                                                nextPageUri);
    }

    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response searchCustomFields(@PathParam("searchKey") final String searchKey,
                                       @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                       @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Pagination<CustomField> customFields = customFieldUserApi.searchCustomFields(searchKey, offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(CustomFieldResource.class, "searchCustomFields", customFields.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                                          QUERY_AUDIT, auditMode.toString()));
        return buildStreamingPaginationResponse(customFields,
                                                new Function<CustomField, CustomFieldJson>() {
                                                    @Override
                                                    public CustomFieldJson apply(final CustomField customField) {
                                                        // TODO Really slow - we should instead try to figure out the account id
                                                        final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(customField.getId(), ObjectType.CUSTOM_FIELD, auditMode.getLevel(), tenantContext);
                                                        return new CustomFieldJson(customField, auditLogs);
                                                    }
                                                },
                                                nextPageUri);
    }
}
