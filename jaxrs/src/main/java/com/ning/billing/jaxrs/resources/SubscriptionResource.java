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
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.jaxrs.json.SubscriptionJson;

@Path("/1.0/subscription")
public class SubscriptionResource {

    @GET
    @Path("/{subscriptionId:\\w+}")
    @Produces(APPLICATION_JSON)
    public Response getSubscription(@PathParam("subscriptionId") String subscriptionId) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createSubscription(SubscriptionJson subscription,
            @QueryParam("requestedDate") String requestedDate) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:\\w+}")
    public Response changeSubscriptionPlan(SubscriptionJson subscription,
            @PathParam("subscriptionId") String subscriptionId,
            @QueryParam("requestedDate") String requestedDate) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @PUT
    @Path("/{subscriptionId:\\w+}/uncancel")
    @Produces(APPLICATION_JSON)
    public Response uncancelSubscriptionPlan(@PathParam("subscriptionId") String subscriptionId) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @DELETE
    @Path("/{subscriptionId:\\w+}")
    @Produces(APPLICATION_JSON)
    public Response cancelSubscriptionPlan(@PathParam("subscriptionId") String subscriptionId,
            @QueryParam("requestedDate") String requestedDate) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
}
