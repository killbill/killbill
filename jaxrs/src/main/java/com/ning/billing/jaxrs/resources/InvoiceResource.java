/*
 * Copyright 2010-2011 Ning, Inc.
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

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceUserApi;

import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentInfoEvent;


@Path(BaseJaxrsResource.INVOICES_PATH)
public class InvoiceResource implements BaseJaxrsResource {


    private static final Logger log = LoggerFactory.getLogger(InvoiceResource.class);

    private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime();
    
    private final AccountUserApi accountApi;
    private final InvoiceUserApi invoiceApi;
    private final PaymentApi paymentApi;
    private final Context context;
    private final JaxrsUriBuilder uriBuilder;
    
    @Inject
    public InvoiceResource(final AccountUserApi accountApi,
            final InvoiceUserApi invoiceApi,
            final PaymentApi paymentApi,            
            final Context context,
            final JaxrsUriBuilder uriBuilder) {
        this.accountApi = accountApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.context = context;
        this.uriBuilder = uriBuilder;
    }
    
    @GET
    @Produces(APPLICATION_JSON)
    public Response getInvoices(@QueryParam(QUERY_ACCOUNT_ID) final String accountId) {
        try {
            
            Preconditions.checkNotNull(accountId, "% query parameter must be specified", QUERY_ACCOUNT_ID);
            accountApi.getAccountById(UUID.fromString(accountId));
            List<Invoice> invoices = invoiceApi.getInvoicesByAccount(UUID.fromString(accountId));
            List<InvoiceJsonSimple> result = new LinkedList<InvoiceJsonSimple>();
            for (Invoice cur : invoices) {
                result.add(new InvoiceJsonSimple(cur));
            }
            return Response.status(Status.OK).entity(result).build();
        } catch (AccountApiException e) {
            return Response.status(Status.NO_CONTENT).build();            
        } catch (NullPointerException e) {
            return Response.status(Status.BAD_REQUEST).build();            
        }
    }

    @GET
    @Path("/{invoiceId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    @Produces(APPLICATION_JSON)
    public Response getInvoice(@PathParam("invoiceId") String invoiceId) {
        Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId));
        InvoiceJsonSimple json = new InvoiceJsonSimple(invoice);
        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createFutureInvoice(final InvoiceJsonSimple invoice,
            @QueryParam(QUERY_ACCOUNT_ID) final String accountId,
            @QueryParam(QUERY_TARGET_DATE) final String targetDate,
            @QueryParam(QUERY_DRY_RUN) @DefaultValue("false") final Boolean dryRun,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        try {
            
            Preconditions.checkNotNull(accountId, "% needs to be specified", QUERY_ACCOUNT_ID);
            Preconditions.checkNotNull(targetDate, "% needs to be specified", QUERY_TARGET_DATE);
            
            DateTime inputDate = (targetDate != null) ? DATE_TIME_FORMATTER.parseDateTime(targetDate) : null;        
            
            accountApi.getAccountById(UUID.fromString(accountId));
            Invoice generatedInvoice = invoiceApi.triggerInvoiceGeneration(UUID.fromString(accountId), inputDate, dryRun.booleanValue(),
                    context.createContext(createdBy, reason, comment));
            if (dryRun) {
                return Response.status(Status.OK).entity(new InvoiceJsonSimple(generatedInvoice)).build();
            } else {
               return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", generatedInvoice.getId());
            }
        } catch (AccountApiException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();  
        } catch (InvoiceApiException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();  
        } catch (NullPointerException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();            
        }
    }
    
    @GET
    @Path("/{invoiceId:\\w+-\\w+-\\w+-\\w+-\\w+}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    public Response getPayments(@PathParam("invoiceId") String invoiceId) {
        try {
            List<PaymentInfoEvent> payments = paymentApi.getPaymentInfo(Collections.singletonList(UUID.fromString(invoiceId)));
            List<PaymentJsonSimple> result =  new ArrayList<PaymentJsonSimple>(payments.size());
            for (PaymentInfoEvent cur : payments) {
                result.add(new PaymentJsonSimple(cur));
            }
            return Response.status(Status.OK).entity(result).build();
        } catch (PaymentApiException e) {
            return Response.status(Status.NOT_FOUND).build();     
        }
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:\\w+-\\w+-\\w+-\\w+-\\w+}/" + PAYMENTS)
    public Response createInstantPayment(PaymentJsonSimple payment,
            @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {
        try {
            Account account = accountApi.getAccountById(UUID.fromString(payment.getAccountId()));
            paymentApi.createPayment(account, UUID.fromString(payment.getInvoiceId()), context.createContext(createdBy, reason, comment));
            Response response = uriBuilder.buildResponse(InvoiceResource.class, "getPayments", payment.getInvoiceId());
            return response;
        } catch (PaymentApiException e) {
            final String error = String.format("Failed to create payment %s", e.getMessage());
            return Response.status(Status.BAD_REQUEST).entity(error).build();
        } catch (AccountApiException e) {
            final String error = String.format("Failed to create payment, can't find account %s", payment.getAccountId());
            return Response.status(Status.BAD_REQUEST).entity(error).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
