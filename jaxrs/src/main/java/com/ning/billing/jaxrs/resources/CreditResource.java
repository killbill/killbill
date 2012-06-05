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

import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.jaxrs.json.CreditJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;

@Singleton
@Path(JaxrsResource.CREDITS_PATH)
public class CreditResource implements JaxrsResource {
    private static final Logger log = LoggerFactory.getLogger(CreditResource.class);

    private final JaxrsUriBuilder uriBuilder;
    private final InvoiceUserApi invoiceUserApi;
    private final AccountUserApi accountUserApi;
    private final Context context;

    @Inject
    public CreditResource(JaxrsUriBuilder uriBuilder, InvoiceUserApi invoiceUserApi, AccountUserApi accountUserApi, Context context) {
        this.uriBuilder = uriBuilder;
        this.invoiceUserApi = invoiceUserApi;
        this.accountUserApi = accountUserApi;
        this.context = context;
    }

    @GET
    @Path("/{creditId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getCredit(@PathParam("creditId") String creditId) {
        try {
            InvoiceItem credit = invoiceUserApi.getCreditById(UUID.fromString(creditId));
            CreditJson creditJson = new CreditJson(credit);

            return Response.status(Response.Status.OK).entity(creditJson).build();
        } catch (InvoiceApiException e) {
            final String error = String.format("Failed to locate credit for id %s", creditId);
            log.info(error, e);
            return Response.status(Response.Status.NO_CONTENT).build();
        }
    }

    @POST
    @Path("/accounts/{accountId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCredit(final CreditJson json,
                                 @PathParam("accountId") final String accountId,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment) {
        try {
            Account account = accountUserApi.getAccountById(UUID.fromString(accountId));

            InvoiceItem credit = invoiceUserApi.insertCredit(account.getId(), json.getCreditAmount(), json.getEffectiveDate(),
                                                             account.getCurrency(), context.createContext(createdBy, reason, comment));

            return uriBuilder.buildResponse(ChargebackResource.class, "getCredit", credit.getId());
        } catch (InvoiceApiException e) {
            final String error = String.format("Failed to create credit %s", json);
            log.info(error, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (AccountApiException e) {
            final String error = String.format("Failed to create credit %s", json);
            log.info(error, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
    }


}


//POST /1.0/accounts/<account_id>/credits	 	 Creates a credit for that account	 201 (CREATED), 400 (BAD_REQUEST), 404 (NOT_FOUND)
//Semantics:
//
//All operations are synchronous
//JSON Credit GET:
// {
//   "accountId" : "theAccountId",
//   "credits" : [{
//      "requestedDate" : "2012-05-12T15:34:22.000Z",
//      "effectiveDate" : "2012-05-12T15:34:22.000Z",
//      "creditAmount" : 54.32,
//      "invoiceId" : "theInvoiceId",
//      "invoiceNumber" : "TheInvoiceNumber",
//      "reason" : "whatever"
//   }]
// }
//
//JSON Credit POST:
// {
//   "creditAmount" : 54.32,
//   "reason" : "whatever",
//   "requestedDate" : "2012-05-12T15:34:22.000Z",
//   "invoiceId" : "theInvoiceId",
//   "invoiceNumber" : "TheInvoiceNumber"
// }