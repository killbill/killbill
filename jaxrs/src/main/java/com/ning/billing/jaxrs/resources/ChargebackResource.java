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

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.Clock;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.jaxrs.json.ChargebackJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.CHARGEBACKS_PATH)
public class ChargebackResource extends JaxRsResourceBase {

    private final InvoicePaymentApi invoicePaymentApi;

    @Inject
    public ChargebackResource(final InvoicePaymentApi invoicePaymentApi,
                              final JaxrsUriBuilder uriBuilder,
                              final TagUserApi tagUserApi,
                              final CustomFieldUserApi customFieldUserApi,
                              final AuditUserApi auditUserApi,
                              final AccountUserApi accountUserApi,
                              final Clock clock,
                              final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.invoicePaymentApi = invoicePaymentApi;
    }

    @GET
    @Path("/{chargebackId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getChargeback(@PathParam("chargebackId") final String chargebackId,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createContext(request);
        final InvoicePayment chargeback = invoicePaymentApi.getChargebackById(UUID.fromString(chargebackId), tenantContext);
        final UUID accountId = invoicePaymentApi.getAccountIdFromInvoicePaymentId(chargeback.getId(), tenantContext);
        final ChargebackJson chargebackJson = new ChargebackJson(accountId, chargeback);

        return Response.status(Response.Status.OK).entity(chargebackJson).build();
    }



    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createChargeback(final ChargebackJson json,
                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                     @HeaderParam(HDR_REASON) final String reason,
                                     @HeaderParam(HDR_COMMENT) final String comment,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePaymentForAttempt(UUID.fromString(json.getPaymentId()), callContext);
        if (invoicePayment == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_NOT_FOUND, json.getPaymentId());
        }
        final InvoicePayment chargeBack = invoicePaymentApi.createChargeback(invoicePayment.getId(), json.getAmount(),
                                                                             callContext);
        return uriBuilder.buildResponse(ChargebackResource.class, "getChargeback", chargeBack.getId());
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE_PAYMENT;
    }
}
