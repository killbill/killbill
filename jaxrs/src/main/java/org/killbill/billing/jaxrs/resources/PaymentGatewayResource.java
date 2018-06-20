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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.ComboHostedPaymentPageJson;
import org.killbill.billing.jaxrs.json.GatewayNotificationJson;
import org.killbill.billing.jaxrs.json.HostedPaymentPageFieldsJson;
import org.killbill.billing.jaxrs.json.HostedPaymentPageFormDescriptorJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentGatewayApi;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Strings;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.WILDCARD;

@Singleton
@Path(JaxrsResource.PAYMENT_GATEWAYS_PATH)
@Api(value = JaxrsResource.PAYMENT_GATEWAYS_PATH, description = "HPP endpoints", tags="PaymentGateway")
public class PaymentGatewayResource extends ComboPaymentResource {

    private final PaymentGatewayApi paymentGatewayApi;

    @Inject
    public PaymentGatewayResource(final JaxrsUriBuilder uriBuilder,
                                  final TagUserApi tagUserApi,
                                  final CustomFieldUserApi customFieldUserApi,
                                  final AuditUserApi auditUserApi,
                                  final AccountUserApi accountUserApi,
                                  final PaymentGatewayApi paymentGatewayApi,
                                  final PaymentApi paymentApi,
                                  final InvoicePaymentApi invoicePaymentApi,
                                  final Clock clock,
                                  final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, clock, context);
        this.paymentGatewayApi = paymentGatewayApi;
    }

    @TimedResource
    @POST
    @Path("/" + HOSTED + "/" + FORM)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Combo API to generate form data to redirect the customer to the gateway", response = HostedPaymentPageFormDescriptorJson.class)
    @ApiResponses(value = {/*@ApiResponse(code = 200, message = "Successful"),*/
                           @ApiResponse(code = 400, message = "Invalid data for Account or PaymentMethod")})
    public Response buildComboFormDescriptor(final ComboHostedPaymentPageJson json,
                                             @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                             @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "ComboHostedPaymentPageJson body should be specified");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final Account account = getOrCreateAccount(json.getAccount(), callContext);

        final Iterable<PluginProperty> paymentMethodPluginProperties = extractPluginProperties(json.getPaymentMethodPluginProperties());
        final UUID paymentMethodId = getOrCreatePaymentMethod(account, json.getPaymentMethod(), paymentMethodPluginProperties, callContext);

        final HostedPaymentPageFieldsJson hostedPaymentPageFields = json.getHostedPaymentPageFields();
        final Iterable<PluginProperty> customFields = extractPluginProperties(hostedPaymentPageFields != null ? hostedPaymentPageFields.getFormFields() : null);

        final HostedPaymentPageFormDescriptor descriptor = paymentGatewayApi.buildFormDescriptorWithPaymentControl(account, paymentMethodId, customFields, pluginProperties, paymentOptions, callContext);
        final HostedPaymentPageFormDescriptorJson result = new HostedPaymentPageFormDescriptorJson(descriptor);

        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Path("/" + HOSTED + "/" + FORM + "/{" + QUERY_ACCOUNT_ID + ":" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Generate form data to redirect the customer to the gateway", response = HostedPaymentPageFormDescriptorJson.class)
    @ApiResponses(value = {/* @ApiResponse(code = 200, message = "Successful"),*/
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response buildFormDescriptor(@PathParam("accountId") final UUID accountId,
                                        final HostedPaymentPageFieldsJson json,
                                        @QueryParam(QUERY_PAYMENT_METHOD_ID) final UUID inputPaymentMethodId,
                                        @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final CallContext callContext = context.createCallContextWithAccountId(accountId, createdBy, reason, comment, request);
        final Account account = accountUserApi.getAccountById(accountId, callContext);
        final UUID paymentMethodId = inputPaymentMethodId == null ? account.getPaymentMethodId() : inputPaymentMethodId;

        validatePaymentMethodForAccount(accountId, paymentMethodId, callContext);

        final Iterable<PluginProperty> customFields = extractPluginProperties(json.getFormFields());

        final HostedPaymentPageFormDescriptor descriptor = paymentGatewayApi.buildFormDescriptorWithPaymentControl(account, paymentMethodId, customFields, pluginProperties, paymentOptions, callContext);
        final HostedPaymentPageFormDescriptorJson result = new HostedPaymentPageFormDescriptorJson(descriptor);

        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Path("/" + NOTIFICATION + "/{" + QUERY_PAYMENT_PLUGIN_NAME + ":" + ANYTHING_PATTERN + "}")
    @Consumes(WILDCARD)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Process a gateway notification", notes = "The response is built by the appropriate plugin")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Successful")})
    public Response processNotification(@PathParam(PATH_PAYMENT_PLUGIN_NAME) final String pluginName,
                                        final String body,
                                        @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final String notificationPayload;
        if (Strings.emptyToNull(body) == null && uriInfo.getRequestUri() != null) {
            notificationPayload = uriInfo.getRequestUri().getRawQuery();
        } else {
            notificationPayload = body;
        }

        // Note: the body is opaque here, as it comes from the gateway. The associated payment plugin will know how to deserialize it though
        final GatewayNotification notification = paymentGatewayApi.processNotificationWithPaymentControl(notificationPayload, pluginName, pluginProperties, paymentOptions, callContext);
        final GatewayNotificationJson result = new GatewayNotificationJson(notification);

        // The plugin told us how to build the response
        return result.toResponse();
    }
}
