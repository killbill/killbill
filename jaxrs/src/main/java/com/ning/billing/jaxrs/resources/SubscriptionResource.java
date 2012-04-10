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
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.jaxrs.json.SubscriptionJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.clock.Clock;

@Path(BaseJaxrsResource.SUBSCRIPTIONS_PATH)
public class SubscriptionResource implements BaseJaxrsResource{

	private static final Logger log = LoggerFactory.getLogger(SubscriptionResource.class);

	private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime();

	private final EntitlementUserApi entitlementApi;
	private final Context context;
	private final JaxrsUriBuilder uriBuilder;	
	private final Clock clock;

	@Inject
	public SubscriptionResource(final JaxrsUriBuilder uriBuilder, final EntitlementUserApi entitlementApi, final Clock clock, final Context context) {
		this.uriBuilder = uriBuilder;
		this.entitlementApi = entitlementApi;
		this.context = context;
		this.clock = clock;
	}

	@GET
	@Path("/{subscriptionId:" + UUID_PATTERN + "}")
	@Produces(APPLICATION_JSON)
	public Response getSubscription(@PathParam("subscriptionId") final String subscriptionId) {

		UUID uuid = UUID.fromString(subscriptionId);
		Subscription subscription = entitlementApi.getSubscriptionFromId(uuid);
		if (subscription == null) {
			return Response.status(Status.NO_CONTENT).build();
		}
		SubscriptionJson json = new SubscriptionJson(subscription, null, null, null);
		return Response.status(Status.OK).entity(json).build();
	}

	@POST
	@Consumes(APPLICATION_JSON)
	@Produces(APPLICATION_JSON)
	public Response createSubscription(SubscriptionJson subscription,
			@QueryParam(QUERY_REQUESTED_DT) String requestedDate) {

		try {
			DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;		
			UUID uuid = UUID.fromString(subscription.getBundleId());

			PlanPhaseSpecifier spec =  new PlanPhaseSpecifier(subscription.getProductName(),
					ProductCategory.valueOf(subscription.getProductCategory()),
					BillingPeriod.valueOf(subscription.getBillingPeriod()), subscription.getPriceList(), null);
			Subscription created = entitlementApi.createSubscription(uuid, spec, inputDate, context.getContext());
			return uriBuilder.buildResponse(SubscriptionResource.class, "getSubscription", created.getId());

		} catch (EntitlementUserApiException e) {
			log.info(String.format("Failed to create subscription %s", subscription), e);
			return Response.status(Status.BAD_REQUEST).build();
		}
	}

	@PUT
	@Produces(APPLICATION_JSON)
	@Consumes(APPLICATION_JSON)
	@Path("/{subscriptionId:" + UUID_PATTERN + "}")
	public Response changeSubscriptionPlan(SubscriptionJson subscription,
			@PathParam("subscriptionId") String subscriptionId,
			@QueryParam(QUERY_REQUESTED_DT) String requestedDate) {

		try {
			UUID uuid = UUID.fromString(subscriptionId);
			Subscription current = entitlementApi.getSubscriptionFromId(uuid);
			if (current == null) {
				return Response.status(Status.NO_CONTENT).build();
			}
			DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
			current.changePlan(subscription.getProductName(),  BillingPeriod.valueOf(subscription.getBillingPeriod()), subscription.getPriceList(), inputDate, context.getContext());

			return getSubscription(subscriptionId);
		} catch (EntitlementUserApiException e) {
			log.info(String.format("Failed to change plan for subscription %s", subscription), e);
			return Response.status(Status.BAD_REQUEST).build();
		}
	}

	@PUT
	@Path("/{subscriptionId:" + UUID_PATTERN + "}/uncancel")
	@Produces(APPLICATION_JSON)
	public Response uncancelSubscriptionPlan(@PathParam("subscriptionId") String subscriptionId) {
		try {
			UUID uuid = UUID.fromString(subscriptionId);
			Subscription current = entitlementApi.getSubscriptionFromId(uuid);
			if (current == null) {
				return Response.status(Status.NO_CONTENT).build();
			}
			current.uncancel(context.getContext());
			return Response.status(Status.OK).build();
		} catch (EntitlementUserApiException e) {
			log.info(String.format("Failed to uncancel plan for subscription %s", subscriptionId), e);
			return Response.status(Status.BAD_REQUEST).build();
		}
	}

	@DELETE
	@Path("/{subscriptionId:" + UUID_PATTERN + "}")
	@Produces(APPLICATION_JSON)
	public Response cancelSubscriptionPlan(@PathParam("subscriptionId") String subscriptionId,
			@QueryParam(QUERY_REQUESTED_DT) String requestedDate) {

		try {
			UUID uuid = UUID.fromString(subscriptionId);
			Subscription current = entitlementApi.getSubscriptionFromId(uuid);
			if (current == null) {
				return Response.status(Status.NO_CONTENT).build();
			}
			DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
			current.cancel(inputDate, false, context.getContext());
			return Response.status(Status.OK).build();
			
		} catch (EntitlementUserApiException e) {
			log.info(String.format("Failed to cancel plan for subscription %s", subscriptionId), e);
			return Response.status(Status.BAD_REQUEST).build();
		}
	}
}
