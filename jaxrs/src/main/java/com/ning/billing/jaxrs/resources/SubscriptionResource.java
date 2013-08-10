package com.ning.billing.jaxrs.resources;

import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.entitlement.api.Subscription;
import com.ning.billing.entitlement.api.SubscriptionApi;
import com.ning.billing.entitlement.api.SubscriptionApiException;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.SUBSCRIPTIONS_PATH)
public class SubscriptionResource extends JaxRsResourceBase {

    private final SubscriptionApi subscriptionApi;

    @Inject
    public SubscriptionResource(final JaxrsUriBuilder uriBuilder,
                                final TagUserApi tagUserApi,
                                final CustomFieldUserApi customFieldUserApi,
                                final AuditUserApi auditUserApi,
                                final AccountUserApi accountUserApi,
                                final SubscriptionApi subscriptionApi,
                                final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, context);
        this.subscriptionApi = subscriptionApi;
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getSubscription(@PathParam("subscriptionId") final String subscriptionId,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final UUID uuid = UUID.fromString(subscriptionId);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(uuid, context.createContext(request));
        // STEPH_ENT missing info in SubscriptionJsonNoEvents (billing dates,...)
        final SubscriptionJsonNoEvents json = new SubscriptionJsonNoEvents(subscription, null);
        return Response.status(Status.OK).entity(json).build();
    }

}
