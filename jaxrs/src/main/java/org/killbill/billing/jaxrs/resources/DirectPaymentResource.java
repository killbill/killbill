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

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.jaxrs.json.DirectPaymentJson;
import org.killbill.billing.jaxrs.json.PaymentJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.DIRECT_PAYMENTS_PATH)
public class DirectPaymentResource extends JaxRsResourceBase {

    private final DirectPaymentApi directPaymentApi;

    @Inject
    public DirectPaymentResource(final JaxrsUriBuilder uriBuilder,
                                 final TagUserApi tagUserApi,
                                 final CustomFieldUserApi customFieldUserApi,
                                 final AuditUserApi auditUserApi,
                                 final AccountUserApi accountUserApi,
                                 final DirectPaymentApi directPaymentApi,
                                 final Clock clock,
                                 final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.directPaymentApi = directPaymentApi;
    }

    @GET
    @Path("/{directPaymentId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    public Response getDirectPayment(@PathParam("directPaymentId") final String directPaymentIdStr,
                                     @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                     @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID directPaymentIdId = UUID.fromString(directPaymentIdStr);
        final DirectPayment payment = directPaymentApi.getPayment(directPaymentIdId, withPluginInfo, pluginProperties, context.createContext(request));
        final DirectPaymentJson result = new DirectPaymentJson(payment, null, null);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @POST
    @Path("/{directPaymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureAuthorization(final PaymentJson json,
                                         @PathParam("directPaymentId") final String directPaymentIdStr,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final UriInfo uriInfo,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        // STEPH_DP error code if no such payment
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID directPaymentId = UUID.fromString(directPaymentIdStr);
        final DirectPayment initialPayment = directPaymentApi.getPayment(directPaymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final DirectPayment payment = directPaymentApi.createCapture(account, directPaymentId, json.getAmount(), currency, pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, DirectPaymentResource.class, "getDirectPayment", payment.getId());
    }

    @DELETE
    @Path("/{directPaymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response voidPayment(@PathParam("directPaymentId") final String directPaymentIdStr,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final UriInfo uriInfo,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID directPaymentId = UUID.fromString(directPaymentIdStr);
        final DirectPayment initialPayment = directPaymentApi.getPayment(directPaymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);

        final DirectPayment payment = directPaymentApi.createVoid(account, directPaymentId, pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, DirectPaymentResource.class, "getDirectPayment", payment.getId());
    }
}
