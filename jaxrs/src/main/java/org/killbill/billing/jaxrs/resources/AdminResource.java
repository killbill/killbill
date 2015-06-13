/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.AdminPaymentJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.AdminPaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Singleton;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.ADMIN_PATH)
@Api(value = JaxrsResource.ADMIN_PATH, description = "Admin operations (will require special privileges)")
public class AdminResource extends JaxRsResourceBase {

    private final AdminPaymentApi adminPaymentApi;

    @Inject
    public AdminResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi, final CustomFieldUserApi customFieldUserApi, final AuditUserApi auditUserApi, final AccountUserApi accountUserApi, final PaymentApi paymentApi, final AdminPaymentApi adminPaymentApi, final Clock clock, final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
        this.adminPaymentApi = adminPaymentApi;
    }


    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/payments/{paymentId:" + UUID_PATTERN + "}/transactions/{paymentTransactionId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Update existing paymentTransaction and associated payment state")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account data supplied")})
    public Response updatePaymentTransactionState(final AdminPaymentJson json,
                                                  @PathParam("paymentId") final String paymentIdStr,
                                                  @PathParam("paymentTransactionId") final String paymentTransactionIdStr,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Payment payment = paymentApi.getPayment(UUID.fromString(paymentIdStr), false, ImmutableList.<PluginProperty>of(), callContext);

        final UUID paymentTransactionId = UUID.fromString(paymentTransactionIdStr);

        final PaymentTransaction paymentTransaction = Iterables.tryFind(payment.getTransactions(), new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return input.getId().equals(paymentTransactionId);
            }
        }).orNull();

        adminPaymentApi.fixPaymentTransactionState(payment, paymentTransaction, TransactionStatus.valueOf(json.getTransactionStatus()),
                                                   json.getLastSuccessPaymentState(), json.getCurrentPaymentStateName(), ImmutableList.<PluginProperty>of(), callContext);
        return Response.status(Status.OK).build();
    }

}
