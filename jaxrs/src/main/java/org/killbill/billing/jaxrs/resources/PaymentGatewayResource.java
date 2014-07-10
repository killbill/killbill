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
import org.killbill.billing.jaxrs.json.GatewayNotificationJson;
import org.killbill.billing.jaxrs.json.HostedPaymentPageFieldsJson;
import org.killbill.billing.jaxrs.json.HostedPaymentPageFormDescriptorJson;
import org.killbill.billing.jaxrs.json.PluginPropertyJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentGatewayApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.WILDCARD;

@Path(JaxrsResource.PAYMENT_GATEWAYS_PATH)
public class PaymentGatewayResource extends JaxRsResourceBase {

    private final PaymentGatewayApi paymentGatewayApi;

    @Inject
    public PaymentGatewayResource(final JaxrsUriBuilder uriBuilder,
                                  final TagUserApi tagUserApi,
                                  final CustomFieldUserApi customFieldUserApi,
                                  final AuditUserApi auditUserApi,
                                  final AccountUserApi accountUserApi,
                                  final PaymentGatewayApi paymentGatewayApi,
                                  final PaymentApi paymentApi,
                                  final Clock clock,
                                  final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
        this.paymentGatewayApi = paymentGatewayApi;
    }

    @POST
    @Path("/" + HOSTED + "/" + FORM + "/{" + QUERY_ACCOUNT_ID + ":" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    // Generate form data to redirect the customer to the gateway
    public Response buildFormDescriptor(final HostedPaymentPageFieldsJson json,
                                        @PathParam(QUERY_ACCOUNT_ID) final String accountIdString,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID accountId = UUID.fromString(accountIdString);
        final Account account = accountUserApi.getAccountById(accountId, callContext);

        final Iterable<PluginProperty> customFields;
        if (json == null) {
            customFields = ImmutableList.<PluginProperty>of();
        } else {
            customFields = Iterables.<PluginPropertyJson, PluginProperty>transform(json.getCustomFields(),
                                                                                   new Function<PluginPropertyJson, PluginProperty>() {
                                                                                       @Override
                                                                                       public PluginProperty apply(final PluginPropertyJson pluginPropertyJson) {
                                                                                           return pluginPropertyJson.toPluginProperty();
                                                                                       }
                                                                                   }
                                                                                  );
        }
        final HostedPaymentPageFormDescriptor descriptor = paymentGatewayApi.buildFormDescriptor(account, customFields, pluginProperties, callContext);
        final HostedPaymentPageFormDescriptorJson result = new HostedPaymentPageFormDescriptorJson(descriptor);

        return Response.status(Response.Status.OK).entity(result).build();
    }

    @POST
    @Path("/" + NOTIFICATION + "/{" + QUERY_PAYMENT_PLUGIN_NAME + ":" + ANYTHING_PATTERN + "}")
    @Consumes(WILDCARD)
    @Produces(APPLICATION_JSON)
    public Response processNotification(final String body,
                                        @PathParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final String notificationPayload;
        if (Strings.emptyToNull(body) == null && uriInfo.getRequestUri() != null) {
            notificationPayload = uriInfo.getRequestUri().getRawQuery();
        } else {
            notificationPayload = body;
        }

        // Note: the body is opaque here, as it comes from the gateway. The associated payment plugin will know how to deserialize it though
        final GatewayNotification notification = paymentGatewayApi.processNotification(notificationPayload, pluginName, pluginProperties, callContext);
        final GatewayNotificationJson result = new GatewayNotificationJson(notification);

        // The plugin told us how to build the response
        return result.toResponse();
    }
}
