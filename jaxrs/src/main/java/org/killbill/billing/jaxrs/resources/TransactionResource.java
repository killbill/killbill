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

import java.util.Collections;
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
import org.killbill.billing.jaxrs.json.AuditLogJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.PaymentJson;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
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
@Path(JaxrsResource.PAYMENT_TRANSACTIONS_PATH)
@Tag(name = "PaymentTransaction", description = "Operations on payment transactions")
public class TransactionResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "transactionId";

    @Inject
    public TransactionResource(final JaxrsUriBuilder uriBuilder,
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

    @TimedResource(name = "getPaymentByTransactionId")
    @GET
    @Path("/{transactionId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve a payment by transaction id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentJson.class))),
                           @ApiResponse(responseCode = "404", description = "Payment not found")})
    public Response getPaymentByTransactionId(@PathParam("transactionId") final UUID transactionId,
                                              @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                              @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                              @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                              @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                              @jakarta.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Payment payment = paymentApi.getPaymentByTransactionId(transactionId, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentJson result = new PaymentJson(payment, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource(name = "getPaymentByTransactionExternalKey")
    @GET
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve a payment by transaction external key")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentJson.class))),
                           @ApiResponse(responseCode = "404", description = "Payment not found")})
    public Response getPaymentByTransactionExternalKey(@Parameter(required = true) @QueryParam(QUERY_TRANSACTION_EXTERNAL_KEY) final String paymentTransactionExternalKey,
                                                       @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                                       @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                                       @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                                       @jakarta.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Payment payment = paymentApi.getPaymentByTransactionExternalKey(paymentTransactionExternalKey, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentJson result = new PaymentJson(payment, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Path("/{transactionId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Mark a pending payment transaction as succeeded or failed")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentJson.class))),
                           @ApiResponse(responseCode = "201", description = "Successfully notifiy state change"),
                           @ApiResponse(responseCode = "400", description = "Invalid paymentId supplied"),
                           @ApiResponse(responseCode = "404", description = "Account or Payment not found")})
    public Response notifyStateChanged(@PathParam("transactionId") final UUID transactionId,
                                       final PaymentTransactionJson json,
                                       @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @jakarta.ws.rs.core.Context final UriInfo uriInfo,
                                       @jakarta.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getPaymentId(), "PaymentTransactionJson paymentId needs to be set",
                             json.getStatus(), "PaymentTransactionJson status needs to be set");

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final UUID paymentId = json.getPaymentId();
        final Payment payment = paymentApi.getPayment(paymentId, false, false, Collections.emptyList(), callContext);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContext);

        final boolean success = TransactionStatus.SUCCESS.name().equals(json.getStatus());
        final Payment result = paymentApi.notifyPendingTransactionOfStateChangedWithPaymentControl(account, transactionId, success, paymentOptions, callContext);

        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", result.getId(), request);
    }


    @TimedResource
    @GET
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve payment transaction custom fields", operationId = "getTransactionCustomFields")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CustomFieldJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid transaction id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @jakarta.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(id, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add custom fields to payment transaction")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CustomField.class)))),
                           @ApiResponse(responseCode = "201", description = "Custom field created successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid transaction id supplied")})
    public Response createTransactionCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Modify custom fields to payment transaction")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid transaction id supplied")})
    public Response modifyTransactionCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Remove custom fields from payment transaction")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid transaction id supplied")})
    public Response deleteTransactionCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve payment transaction tags", operationId = "getTransactionTags")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagJson.class)))),
                           @ApiResponse(responseCode = "400", description = "Invalid transaction id supplied"),
                           @ApiResponse(responseCode = "404", description = "Invoice not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID id,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, PaymentApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Payment payment = paymentApi.getPaymentByTransactionId(id, false, false, Collections.emptyList(), tenantContext);
        return super.getTags(payment.getAccountId(), id, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Add tags to payment transaction")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagJson.class)))),
                           @ApiResponse(responseCode = "201", description = "Tag created successfully"),
                           @ApiResponse(responseCode = "400", description = "Invalid transaction id supplied")})
    public Response createTransactionTags(@PathParam(ID_PARAM_NAME) final UUID id,
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
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Remove tags from payment transaction")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Successful operation"),
                           @ApiResponse(responseCode = "400", description = "Invalid transaction id supplied")})
    public Response deleteTransactionTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                          @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @jakarta.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(id, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve payment transaction audit logs with history by id")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AuditLogJson.class)))),
                           @ApiResponse(responseCode = "404", description = "Account not found")})
    public Response getTransactionAuditLogsWithHistory(@PathParam("transactionId") final UUID transactionId,
                                                   @jakarta.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final List<AuditLogWithHistory> auditLogWithHistory = paymentApi.getPaymentTransactionAuditLogsWithHistoryForId(transactionId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.TRANSACTION;
    }

}
