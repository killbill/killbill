/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.jaxrs.resources;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.jaxrs.json.PluginInfoJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginsInfoApi;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PLUGINS_INFO_PATH)
@Api(value = JaxrsResource.PLUGINS_INFO_PATH, description = "Operations on plugins", tags="PluginInfo")
public class PluginInfoResource extends JaxRsResourceBase {

    private final PluginsInfoApi pluginsInfoApi;

    @Inject
    public PluginInfoResource(final JaxrsUriBuilder uriBuilder,
                              final TagUserApi tagUserApi,
                              final CustomFieldUserApi customFieldUserApi,
                              final AuditUserApi auditUserApi,
                              final AccountUserApi accountUserApi,
                              final PaymentApi paymentApi,
                              final InvoicePaymentApi invoicePaymentApi,
                              final PluginsInfoApi pluginsInfoApi,
                              final Clock clock,
                              final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.pluginsInfoApi = pluginsInfoApi;
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve the list of registered plugins", response = PluginInfoJson.class, responseContainer = "List")
    public Response getPluginsInfo(@javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        return Response.status(Status.OK).entity(ImmutableList.copyOf(Iterables.transform(pluginsInfoApi.getPluginsInfo(), new Function<PluginInfo, PluginInfoJson>() {
            @Override
            public PluginInfoJson apply(final PluginInfo input) {
                return new PluginInfoJson(input);
            }
        }))).build();
    }

}
