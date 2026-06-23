/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.AuditLogJson;
import org.killbill.billing.jaxrs.json.TagDefinitionJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.commons.utils.Preconditions;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.tag.TagDefinition;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.api.annotation.TimedResource;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.TAG_DEFINITIONS_PATH)
@Tag(name = "TagDefinition", description = "Operations on tag definitions")
public class TagDefinitionResource extends JaxRsResourceBase {

    @Inject
    public TagDefinitionResource(final JaxrsUriBuilder uriBuilder,
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
    @Produces(APPLICATION_JSON)
    @Operation(summary = "List tag definitions")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagDefinitionJson.class))))})
    public Response getTagDefinitions(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode) {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final List<TagDefinition> tagDefinitions = tagUserApi.getTagDefinitions(tenantContext);

        final Collection<TagDefinitionJson> result = new LinkedList<>();
        for (final TagDefinition tagDefinition : tagDefinitions) {
            final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(tagDefinition.getId(), ObjectType.TAG_DEFINITION, auditMode.getLevel(), tenantContext);
            result.add(new TagDefinitionJson(tagDefinition, auditLogs));
        }

        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @GET
    @Path("/{tagDefinitionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve a tag definition")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TagDefinitionJson.class))),
                           @ApiResponse(responseCode = "400", description = "Invalid tagDefinitionId supplied")})
    public Response getTagDefinition(@PathParam("tagDefinitionId") final UUID tagDefId,
                                     @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                     @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final TagDefinition tagDefinition = tagUserApi.getTagDefinition(tagDefId, tenantContext);
        final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(tagDefinition.getId(), ObjectType.TAG_DEFINITION, auditMode.getLevel(), tenantContext);
        final TagDefinitionJson json = new TagDefinitionJson(tagDefinition, auditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Create a tag definition")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Tag definition created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TagDefinitionJson.class))),
                           @ApiResponse(responseCode = "400", description = "Invalid name or description supplied")})
    public Response createTagDefinition(final TagDefinitionJson json,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                        @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws TagDefinitionApiException {
        // Checked as the database layer as well, but bail early and return 400 instead of 500
        verifyNonNullOrEmpty(json, "TagDefinitionJson body should be specified");
        verifyNonNullOrEmpty(json.getName(), "TagDefinition name needs to be set",
                             json.getDescription(), "TagDefinition description needs to be set");
        Preconditions.checkArgument(json.getApplicableObjectTypes() != null && !json.getApplicableObjectTypes().isEmpty(),
                                    "Applicable object types must be set");


        final TagDefinition createdTagDef = tagUserApi.createTagDefinition(json.getName(), json.getDescription(), json.getApplicableObjectTypes(), context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(uriInfo, TagDefinitionResource.class, "getTagDefinition", createdTagDef.getId(), request);
    }

    @TimedResource
    @DELETE
    @Path("/{tagDefinitionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Delete a tag definition")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid tagDefinitionId supplied")})
    public Response deleteTagDefinition(@PathParam("tagDefinitionId") final UUID tagDefId,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        tagUserApi.deleteTagDefinition(tagDefId, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Path("/{tagDefinitionId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve tag definition audit logs with history by id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AuditLogJson.class)))),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public Response getTagDefinitionAuditLogsWithHistory(@PathParam("tagDefinitionId") final UUID tagDefinitionId,
                                                   @jakarta.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final List<AuditLogWithHistory> auditLogWithHistory = tagUserApi.getTagDefinitionAuditLogsWithHistoryForId(tagDefinitionId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.TAG_DEFINITION;
    }
}
