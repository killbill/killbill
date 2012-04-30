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
import java.util.Collection;
import java.util.List;
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
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.BundleJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;


@Singleton
@Path(BaseJaxrsResource.ACCOUNTS_PATH)
public class AccountResource implements BaseJaxrsResource {

    private static final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final AccountUserApi accountApi;
    private final EntitlementUserApi entitlementApi;
    private final Context context;
    private final JaxrsUriBuilder uriBuilder;

    @Inject
    public AccountResource(final JaxrsUriBuilder uriBuilder, final AccountUserApi accountApi, final EntitlementUserApi entitlementApi, final Context context) {
        this.uriBuilder = uriBuilder;
    	this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.context = context;
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getAccount(@PathParam("accountId") String accountId) {
        try {
            Account account = accountApi.getAccountById(UUID.fromString(accountId));

            AccountJson json = new AccountJson(account);
            return Response.status(Status.OK).entity(json).build();
        } catch (AccountApiException e) {
            log.warn("Failed to find account.", e);
            return Response.status(Status.NO_CONTENT).build();
        }
        
    }

    @GET
    @Path("/{accountId:" + UUID_PATTERN + "}/" + BUNDLES)
    @Produces(APPLICATION_JSON)
    public Response getAccountBundles(@PathParam("accountId") String accountId) {

        try {
            UUID uuid = UUID.fromString(accountId);
            accountApi.getAccountById(uuid);

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(uuid);
            Collection<BundleJson> result = Collections2.transform(bundles, new Function<SubscriptionBundle, BundleJson>() {
                @Override
                public BundleJson apply(SubscriptionBundle input) {
                    return new BundleJson(input);
                }
            });
            return Response.status(Status.OK).entity(result).build();
        } catch (AccountApiException e) {
            log.warn("Failed to find account.", e);
            return Response.status(Status.NO_CONTENT).build();
        }
    }

    
    @GET
    @Produces(APPLICATION_JSON)
    public Response getAccountByKey(@QueryParam(QUERY_EXTERNAL_KEY) String externalKey) {
        try {
            Account account = null;
            if (externalKey != null) {
                account = accountApi.getAccountByKey(externalKey);
            }
            if (account == null) {
                return Response.status(Status.NO_CONTENT).build();
            }
            AccountJson json = new AccountJson(account);
            return Response.status(Status.OK).entity(json).build();
        } catch (AccountApiException e) {
            log.warn("Failed to find account.", e);
            return Response.status(Status.NO_CONTENT).build();
        }
    }

    
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createAccount(AccountJson json) {

        try {
        	
            AccountData data = json.toAccountData();
            final Account account = accountApi.createAccount(data, null, null, context.createContext());
            URI uri = UriBuilder.fromPath(account.getId().toString()).build();
            return uriBuilder.buildResponse(AccountResource.class, "getAccount", account.getId());
        } catch (AccountApiException e) {
            log.info(String.format("Failed to create account %s", json), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/{accountId:" + UUID_PATTERN + "}")
    public Response updateAccount(AccountJson json, @PathParam("accountId") String accountId) {
        try {
            AccountData data = json.toAccountData();
            UUID uuid = UUID.fromString(accountId);
            accountApi.updateAccount(uuid, data, context.createContext());
            return getAccount(accountId);
        } catch (AccountApiException e) {
        	if (e.getCode() == ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID.getCode()) {
        		return Response.status(Status.NO_CONTENT).build();        		
        	} else {
        		log.info(String.format("Failed to update account %s with %s", accountId, json), e);
        		return Response.status(Status.BAD_REQUEST).build();
        	}
        }
    }

    // Not supported
    @DELETE
    @Path("/{accountId:" + UUID_PATTERN + "}")
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
