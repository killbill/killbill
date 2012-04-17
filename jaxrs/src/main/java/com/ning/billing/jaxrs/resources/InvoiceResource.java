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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.jaxrs.json.InvoiceJson;



@Path("/1.0/invoice")
public class InvoiceResource {


    @GET
    @Produces(APPLICATION_JSON)
    public Response getInvoices(@QueryParam("accountId") String accountId) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @GET
    @Path("/{invoiceId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    @Produces(APPLICATION_JSON)
    public Response getInvoice(@PathParam("invoiceId") String accountId) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @POST
    @Path("/{accountId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createFutureInvoice(InvoiceJson invoice,
            @PathParam("accountId") String accountId,
            @QueryParam("targetDate") String targetDate) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
}
