/*
 * Copyright 2010-2012 Ning, Inc.
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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.jaxrs.json.OverdueStateJson;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.config.api.OverdueError;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.OVERDUE_PATH)
public class OverdueResource implements JaxrsResource {

    private final OverdueUserApi overdueApi;
    private final AccountUserApi accountApi;
    private final EntitlementUserApi entitlementApi;

    @Inject
    public OverdueResource(final OverdueUserApi overdueApi,
                           final AccountUserApi accountApi,
                           final EntitlementUserApi entitlementApi) {
        this.overdueApi = overdueApi;
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
    }

    @GET
    @Path("/" + ACCOUNTS + "/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getOverdueAccount(@PathParam("accountId") final String accountId) throws AccountApiException, OverdueError, OverdueApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId));
        final OverdueState<Account> overdueState = overdueApi.getOverdueStateFor(account);

        return Response.status(Status.OK).entity(new OverdueStateJson(overdueState)).build();
    }

    @GET
    @Path("/" + BUNDLES + "/{bundleId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getOverdueBundle(@PathParam("bundleId") final String bundleId) throws EntitlementUserApiException, OverdueError, OverdueApiException {
        final SubscriptionBundle bundle = entitlementApi.getBundleFromId(UUID.fromString(bundleId));
        final OverdueState<SubscriptionBundle> overdueState = overdueApi.getOverdueStateFor(bundle);

        return Response.status(Status.OK).entity(new OverdueStateJson(overdueState)).build();
    }

    @GET
    @Path("/" + SUBSCRIPTIONS + "/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getOverdueSubscription(@PathParam("subscriptionId") final String subscriptionId) throws EntitlementUserApiException, OverdueError, OverdueApiException {
        final Subscription subscription = entitlementApi.getSubscriptionFromId(UUID.fromString(subscriptionId));
        final OverdueState<Subscription> overdueState = overdueApi.getOverdueStateFor(subscription);

        return Response.status(Status.OK).entity(new OverdueStateJson(overdueState)).build();
    }
}
