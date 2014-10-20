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

import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableList;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENT_TRANSACTIONS)
@Api(value = JaxrsResource.PAYMENT_TRANSACTIONS, description = "Operations on payment transactions")
public class TransactionResource extends JaxRsResourceBase {

    @Inject
    public TransactionResource(final JaxrsUriBuilder uriBuilder,
                               final TagUserApi tagUserApi,
                               final CustomFieldUserApi customFieldUserApi,
                               final AuditUserApi auditUserApi,
                               final AccountUserApi accountUserApi,
                               final PaymentApi paymentApi,
                               final Clock clock,
                               final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
    }

    @Timed
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
        final Payment payment = paymentApi.getPayment(paymentId, false, ImmutableList.<PluginProperty>of(), callContext);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContext);

        final boolean success = TransactionStatus.SUCCESS.name().equals(json.getStatus());
        final Payment result = paymentApi.notifyPendingTransactionOfStateChanged(account, UUID.fromString(transactionIdStr), success, callContext);
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", result.getId());
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.TRANSACTION;
    }

}
