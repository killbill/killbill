/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.PaymentJson;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.collect.ImmutableList;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENT_TRANSACTIONS_PATH)
@Api(value = JaxrsResource.PAYMENT_TRANSACTIONS_PATH, description = "Operations on payment transactions")
public class TransactionResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "transactionId";

    @Inject
    public TransactionResource(final JaxrsUriBuilder uriBuilder,
                               final TagUserApi tagUserApi,
                               final CustomFieldUserApi customFieldUserApi,
                               final AuditUserApi auditUserApi,
                               final AccountUserApi accountUserApi,
                               final PaymentApi paymentApi,
                               final Clock clock,
                               final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
    }

    @TimedResource(name = "getPaymentByTransactionId")
    @GET
    @Path("/{transactionId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment by transaction id", response = PaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Payment not found")})
    public Response getPaymentByTransactionId(@PathParam("transactionId") final String transactionIdStr,
                                              @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                              @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                              @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                              @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID transactionIdId = UUID.fromString(transactionIdStr);
        final TenantContext tenantContext = context.createContext(request);
        final Payment payment = paymentApi.getPaymentByTransactionId(transactionIdId, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentJson result = new PaymentJson(payment, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Path("/{transactionId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Mark a pending payment transaction as succeeded or failed")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or Payment not found")})
    public Response notifyStateChanged(final PaymentTransactionJson json,
                                       @PathParam("transactionId") final String transactionIdStr,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getPaymentId(), "PaymentTransactionJson paymentId needs to be set",
                             json.getStatus(), "PaymentTransactionJson status needs to be set");

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID paymentId = UUID.fromString(json.getPaymentId());
        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), callContext);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContext);

        final boolean success = TransactionStatus.SUCCESS.name().equals(json.getStatus());
        final Payment result = paymentApi.notifyPendingTransactionOfStateChanged(account, UUID.fromString(transactionIdStr), success, callContext);
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", result.getId(), request);
    }


    @TimedResource
    @GET
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment transaction custom fields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid transaction id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @TimedResource
    @POST
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to payment transaction")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid transaction id supplied")})
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request), uriInfo, request);
    }

    @TimedResource
    @DELETE
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from payment transaction")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid transaction id supplied")})
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment transaction tags", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid transaction id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Payment payment = paymentApi.getPaymentByTransactionId(UUID.fromString(id), false, false, ImmutableList.<PluginProperty>of(), tenantContext);
        return super.getTags(payment.getAccountId(), UUID.fromString(id), auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to payment transaction")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid transaction id supplied")})
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{transactionId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from payment transaction")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid transaction id supplied")})
    public Response deleteTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.TRANSACTION;
    }

}
