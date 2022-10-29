/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.jaxrs.resources;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.AuditLogJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.commons.utils.Preconditions;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.api.annotation.TimedResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CUSTOM_FIELDS_PATH)
@Api(value = JaxrsResource.CUSTOM_FIELDS_PATH, description = "Operations on custom fields", tags="CustomField")
public class CustomFieldResource extends JaxRsResourceBase {

    @Inject
    public CustomFieldResource(final JaxrsUriBuilder uriBuilder,
                               final TagUserApi tagUserApi,
                               final CustomFieldUserApi customFieldUserApi,
                               final AuditUserApi auditUserApi,
                               final AccountUserApi accountUserApi,
                               final PaymentApi paymentApi,
                               final InvoicePaymentApi invoicePaymentApi,
                               final Clock clock,
                               final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
    }

    @TimedResource
    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List custom fields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getCustomFields(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                    @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<CustomField> customFields = customFieldUserApi.getCustomFields(offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(CustomFieldResource.class,
                                                    "getCustomFields",
                                                    customFields.getNextOffset(),
                                                    limit,
                                                    Map.of(QUERY_AUDIT, auditMode.getLevel().toString()),
                                                    Collections.emptyMap());

        return buildStreamingPaginationResponse(customFields,
                                                customField -> {
                                                    // TODO Really slow - we should instead try to figure out the account id
                                                    final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(customField.getId(), ObjectType.CUSTOM_FIELD, auditMode.getLevel(), tenantContext);
                                                    return new CustomFieldJson(customField, auditLogs);
                                                },
                                                nextPageUri);
    }


    @TimedResource
    @GET
    @Path("/" + SEARCH )
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Search custom fields by type, name and optional value", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response searchCustomFieldsByTypeName(@QueryParam("objectType") final String objectType,
                                                 @QueryParam("fieldName") final String fieldName,
                                                 @Nullable @QueryParam("fieldValue") final String fieldValue,
                                                 @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                                 @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                                 @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                                 @javax.ws.rs.core.Context final HttpServletRequest request) {

        Preconditions.checkNotNull(objectType, "objectType in searchCustomFieldsByTypeName() is null");
        Preconditions.checkNotNull(fieldName, "fieldName in searchCustomFieldsByTypeName() is null");

        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<CustomField> customFields = fieldValue != null ?
                                                     customFieldUserApi.searchCustomFields(fieldName, fieldValue, ObjectType.valueOf(objectType), offset, limit, tenantContext) :
                                                     customFieldUserApi.searchCustomFields(fieldName,  ObjectType.valueOf(objectType), offset, limit, tenantContext);

        final URI nextPageUri = uriBuilder.nextPage(CustomFieldResource.class,
                                                    "searchCustomFields",
                                                    customFields.getNextOffset(),
                                                    limit,
                                                    Map.of("objectType", objectType,
                                                           "fieldName", fieldName,
                                                           "fieldValue", Objects.requireNonNullElse(fieldValue, ""),
                                                           QUERY_AUDIT, auditMode.getLevel().toString()),
                                                    Collections.emptyMap());
        return buildStreamingPaginationResponse(customFields,
                                                customField -> {
                                                    // TODO Really slow - we should instead try to figure out the account id
                                                    final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(customField.getId(), ObjectType.CUSTOM_FIELD, auditMode.getLevel(), tenantContext);
                                                    return new CustomFieldJson(customField, auditLogs);
                                                },
                                                nextPageUri);
    }



    @TimedResource
    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Search custom fields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response searchCustomFields(@PathParam("searchKey") final String searchKey,
                                       @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                       @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<CustomField> customFields = customFieldUserApi.searchCustomFields(searchKey, offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(CustomFieldResource.class,
                                                    "searchCustomFields",
                                                    customFields.getNextOffset(),
                                                    limit,
                                                    Map.of(QUERY_AUDIT, auditMode.getLevel().toString()),
                                                    Map.of("searchKey", searchKey));
        return buildStreamingPaginationResponse(customFields,
                                                customField -> {
                                                    // TODO Really slow - we should instead try to figure out the account id
                                                    final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(customField.getId(), ObjectType.CUSTOM_FIELD, auditMode.getLevel(), tenantContext);
                                                    return new CustomFieldJson(customField, auditLogs);
                                                },
                                                nextPageUri);
    }



    @TimedResource
    @GET
    @Path("/{customFieldId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve custom field audit logs with history by id", response = AuditLogJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response getCustomFieldAuditLogsWithHistory(@PathParam("customFieldId") final UUID customFieldId,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final List<AuditLogWithHistory> auditLogWithHistory = customFieldUserApi.getCustomFieldAuditLogsWithHistoryForId(customFieldId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }
}
