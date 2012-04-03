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

import java.net.URI;
import java.util.UUID;

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
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.util.Context;


@Singleton
@Path("/1.0/account")
public class AccountResource {

    private static final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final AccountUserApi accountApi;
    private final Context context;

    @Inject
    public AccountResource(final AccountUserApi accountApi, final Context context) {
        this.accountApi = accountApi;
        this.context = context;
    }

    @GET
    @Path("/{accountId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    @Produces(APPLICATION_JSON)
    public Response getAccount(@PathParam("accountId") String accountId) {
        Account account = accountApi.getAccountById(UUID.fromString(accountId));
        if (account == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        AccountJson json = new AccountJson(account);
        return Response.status(Status.OK).entity(json).build();
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getAccountByKey(@QueryParam("externalKey") String externalKey) {
        Account account = null;
        if (externalKey != null) {
            account = accountApi.getAccountByKey(externalKey);
        }
        if (account == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        AccountJson json = new AccountJson(account);
        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createAccount(AccountJson json) {

        try {
            AccountData data = json.toAccountData();
            final Account account = accountApi.createAccount(data, null, null, context.getContext());
            URI uri = UriBuilder.fromPath(account.getId().toString()).build();
            Response.ResponseBuilder ri = Response.created(uri);
            return ri.entity(new Object() {
                public URI getUri() {
                    return UriBuilder.fromResource(AccountResource.class).path(AccountResource.class, "getAccount").build(account.getId());
                }
            }).build();
        } catch (AccountApiException e) {
            log.info(String.format("Failed to create account %s", json), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    public Response updateAccount(AccountJson json, @PathParam("accountId") String accountId) {
        try {
            AccountData data = json.toAccountData();
            accountApi.updateAccount(accountId, data, context.getContext());
            return Response.status(Status.NO_CONTENT).build();
        } catch (AccountApiException e) {
            log.info(String.format("Failed to update account %s with %s", accountId, json), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
    }

    // Not supported
    @DELETE
    @Path("/{accountId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    @Produces(APPLICATION_JSON)
    public Response cancelAccount(@PathParam("accountId") String accountId) {
        /*
        try {
            accountApi.cancelAccount(accountId);
            return Response.status(Status.NO_CONTENT).build();
        } catch (AccountApiException e) {
            log.info(String.format("Failed to cancel account %s", accountId), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
       */
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
}
