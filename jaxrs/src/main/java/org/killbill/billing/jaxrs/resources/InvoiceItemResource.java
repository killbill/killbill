/*
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
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.AuditLogJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.api.annotation.TimedResource;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.INVOICES_ITEMS_PATH)
@Tag(name = "InvoiceItem", description = "Operations on invoice items")
public class InvoiceItemResource extends JaxRsResourceBase {
    private static final String ID_PARAM_NAME = "invoiceItemId";

    private final InvoiceUserApi invoiceApi;

    @Inject
    public InvoiceItemResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi, final CustomFieldUserApi customFieldUserApi,
                               final AuditUserApi auditUserApi, final AccountUserApi accountUserApi, final InvoiceUserApi invoiceApi, final PaymentApi paymentApi,
                               final InvoicePaymentApi invoicePaymentApi, final SubscriptionApi subscriptionApi, final Clock clock, final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, subscriptionApi, clock, context);
        this.invoiceApi = invoiceApi;
    }

    @TimedResource
    @GET
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve invoice item audit logs with history by id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AuditLogJson.class)))),
                           @ApiResponse(responseCode = "404", description = "Invoice item not found")})
    public Response getInvoiceItemAuditLogsWithHistory(@PathParam("invoiceItemId") final UUID invoiceItemId,
                                                       @jakarta.ws.rs.core.Context final HttpServletRequest request) {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final List<AuditLogWithHistory> auditLogWithHistory = invoiceApi.getInvoiceItemAuditLogsWithHistoryForId(invoiceItemId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }


    @TimedResource
    @GET
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve invoice item custom fields", operationId = "getInvoiceItemCustomFields")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CustomFieldJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice item id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @jakarta.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(id, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add custom fields to invoice item")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Custom field created successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CustomField.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice item id supplied")})
    public Response createInvoiceItemCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                  final List<CustomFieldJson> customFields,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                                  @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request), uriInfo, request);
    }


    @TimedResource
    @PUT
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Modify custom fields to invoice item")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice item id supplied")})
    public Response modifyInvoiceItemCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                  final List<CustomFieldJson> customFields,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @jakarta.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }


    @TimedResource
    @DELETE
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Remove custom fields from invoice item")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice item id supplied")})
    public Response deleteInvoiceItemCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                  @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @jakarta.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(id, customFieldList,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve invoice item tags", operationId = "getInvoiceItemTags")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice item id supplied"),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID id,
                            @Parameter(required = true) @QueryParam(QUERY_ACCOUNT_ID) final UUID accountId,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, AccountApiException {
        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final Account account = accountUserApi.getAccountById(accountId, tenantContext);

        return super.getTags(account.getId(), id, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add tags to invoice item")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Tag created successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice item id supplied")})
    public Response createInvoiceItemTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                          final List<UUID> tagList,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @jakarta.ws.rs.core.Context final UriInfo uriInfo,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(id, tagList, uriInfo,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{invoiceItemId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Remove tags from invoice item")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid invoice item id supplied")})
    public Response deleteInvoiceItemTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                          @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(id, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_ITEM;
    }
}
