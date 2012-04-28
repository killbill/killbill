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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.jaxrs.json.BundleJsonNoSubsciptions;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;

@Path(BaseJaxrsResource.BUNDLES_PATH)
public class BundleResource implements BaseJaxrsResource {

	private static final Logger log = LoggerFactory.getLogger(BundleResource.class);

	private final EntitlementUserApi entitlementApi;
	private final Context context;
    private final JaxrsUriBuilder uriBuilder;	

    @Inject
	public BundleResource(final JaxrsUriBuilder uriBuilder, final EntitlementUserApi entitlementApi, final Context context) {
	    this.uriBuilder = uriBuilder;
		this.entitlementApi = entitlementApi;
		this.context = context;
	}

	@GET
	@Path("/{bundleId:"  + UUID_PATTERN + "}")
	@Produces(APPLICATION_JSON)
	public Response getBundle(@PathParam("bundleId") final String bundleId) {
		SubscriptionBundle bundle = entitlementApi.getBundleFromId(UUID.fromString(bundleId));
		if (bundle == null) {
			return Response.status(Status.NO_CONTENT).build();
		}
		BundleJsonNoSubsciptions json = new BundleJsonNoSubsciptions(bundle);
		return Response.status(Status.OK).entity(json).build();
	}

	@GET
	@Produces(APPLICATION_JSON)
	public Response getBundleByKey(@QueryParam(QUERY_EXTERNAL_KEY) final String externalKey) {
		SubscriptionBundle bundle = entitlementApi.getBundleForKey(externalKey);
		if (bundle == null) {
			return Response.status(Status.NO_CONTENT).build();
		}
		BundleJsonNoSubsciptions json = new BundleJsonNoSubsciptions(bundle);
		return Response.status(Status.OK).entity(json).build();
	}

	@POST
	@Consumes(APPLICATION_JSON)
	@Produces(APPLICATION_JSON)
	public Response createBundle(final BundleJsonNoSubsciptions json) {
		try {
			UUID accountId = UUID.fromString(json.getAccountId());
			final SubscriptionBundle bundle = entitlementApi.createBundleForAccount(accountId, json.getExternalKey(), context.createContext());
            return uriBuilder.buildResponse(BundleResource.class, "getBundle", bundle.getId());
		} catch (EntitlementUserApiException e) {
			log.info(String.format("Failed to create bundle %s", json), e);
			return Response.status(Status.BAD_REQUEST).build();
		}
	}
	
	@GET
	@Path("/{bundleId:" + UUID_PATTERN + "}/" + SUBSCRIPTIONS)
	@Produces(APPLICATION_JSON)
	public Response getBundleSubscriptions(@PathParam("bundleId") final String bundleId) {
		
		UUID uuid = UUID.fromString(bundleId);
		SubscriptionBundle bundle = entitlementApi.getBundleFromId(uuid);
		if (bundle == null) {
			return Response.status(Status.NO_CONTENT).build();
		}
		List<Subscription> bundles = entitlementApi.getSubscriptionsForBundle(uuid);
		Collection<SubscriptionJsonNoEvents> result =  Collections2.transform(bundles, new Function<Subscription, SubscriptionJsonNoEvents>() {
			@Override
			public SubscriptionJsonNoEvents apply(Subscription input) {
				return new SubscriptionJsonNoEvents(input);
			}
		});
		return Response.status(Status.OK).entity(result).build();
	}
}
