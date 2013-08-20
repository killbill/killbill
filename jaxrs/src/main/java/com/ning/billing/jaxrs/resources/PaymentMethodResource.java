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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.Clock;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.PAYMENT_METHODS_PATH)
public class PaymentMethodResource extends JaxRsResourceBase {

    private final PaymentApi paymentApi;

    @Inject
    public PaymentMethodResource(final PaymentApi paymentApi,
                                 final AccountUserApi accountUserApi,
                                 final JaxrsUriBuilder uriBuilder,
                                 final TagUserApi tagUserApi,
                                 final CustomFieldUserApi customFieldUserApi,
                                 final AuditUserApi auditUserApi,
                                 final Clock clock,
                                 final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.paymentApi = paymentApi;
    }

    @GET
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getPaymentMethod(@PathParam("paymentMethodId") final String paymentMethodId,
                                     @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);

        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(UUID.fromString(paymentMethodId), false, withPluginInfo, tenantContext);
        final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), tenantContext);
        final PaymentMethodJson json = PaymentMethodJson.toPaymentMethodJson(account, paymentMethod);

        return Response.status(Status.OK).entity(json).build();
    }

    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response searchPaymentMethods(@PathParam("searchKey") final String searchKey,
                                         @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_NAME) final String pluginName,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final TenantContext tenantContext = context.createContext(request);

        // Search the plugin(s)
        final List<PaymentMethod> paymentMethods;
        if (Strings.isNullOrEmpty(pluginName)) {
            paymentMethods = paymentApi.searchPaymentMethods(searchKey, tenantContext);
        } else {
            paymentMethods = paymentApi.searchPaymentMethods(searchKey, pluginName, tenantContext);
        }

        // Lookup the associated account(s)
        final Map<UUID, Account> accounts = new HashMap<UUID, Account>();
        for (final PaymentMethod paymentMethod : paymentMethods) {
            if (accounts.get(paymentMethod.getAccountId()) == null) {
                final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), tenantContext);
                accounts.put(paymentMethod.getAccountId(), account);
            }
        }

        final List<PaymentMethodJson> json = Lists.transform(paymentMethods, new Function<PaymentMethod, PaymentMethodJson>() {
            @Override
            public PaymentMethodJson apply(final PaymentMethod paymentMethod) {
                return PaymentMethodJson.toPaymentMethodJson(accounts.get(paymentMethod.getAccountId()), paymentMethod);
            }
        });
        return Response.status(Status.OK).entity(json).build();
    }

    @DELETE
    @Produces(APPLICATION_JSON)
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}")
    public Response deletePaymentMethod(@PathParam("paymentMethodId") final String paymentMethodId,
                                        @QueryParam(QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF) @DefaultValue("false") final Boolean deleteDefaultPaymentMethodWithAutoPayOff,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(UUID.fromString(paymentMethodId), false, false, callContext);
        final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), callContext);

        paymentApi.deletedPaymentMethod(account, UUID.fromString(paymentMethodId), deleteDefaultPaymentMethodWithAutoPayOff, callContext);

        return Response.status(Status.OK).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT_METHOD;
    }
}
