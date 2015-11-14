/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.jaxrs.json.NodeInfoJson;
import org.killbill.billing.jaxrs.json.PluginInfoJson;
import org.killbill.billing.jaxrs.json.PluginInfoJson.PluginServiceInfoJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginServiceInfo;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.nodes.KillbillNodesApi;
import org.killbill.billing.util.nodes.NodeInfo;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.NODES_INFO_PATH)
@Api(value = JaxrsResource.NODES_INFO_PATH, description = "Operations to retrieve nodes info")
public class NodesInfoResource extends JaxRsResourceBase {

    private final KillbillNodesApi killbillInfoApi;

    @Inject
    public NodesInfoResource(final JaxrsUriBuilder uriBuilder,
                             final TagUserApi tagUserApi,
                             final CustomFieldUserApi customFieldUserApi,
                             final AuditUserApi auditUserApi,
                             final AccountUserApi accountUserApi,
                             final PaymentApi paymentApi,
                             final KillbillNodesApi killbillInfoApi,
                             final Clock clock,
                             final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
        this.killbillInfoApi = killbillInfoApi;
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve all the nodes infos", response = PluginInfoJson.class, responseContainer = "List")
    public Response getNodesInfo(@javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {

        final Iterable<NodeInfo> nodeInfos = killbillInfoApi.getNodesInfo();

        final List<NodeInfoJson> nodeInfosJson = ImmutableList.copyOf(Iterables.transform(nodeInfos, new Function<NodeInfo, NodeInfoJson>() {
            @Override
            public NodeInfoJson apply(final NodeInfo input) {
                final Iterable<PluginInfo> pluginsInfo = input.getPluginInfo();

                final List<PluginInfoJson> pluginsInfoJson = ImmutableList.copyOf(Iterables.transform(pluginsInfo, new Function<PluginInfo, PluginInfoJson>() {
                    @Override
                    public PluginInfoJson apply(final PluginInfo input) {

                        final Set<PluginServiceInfo> services = input.getServices();
                        final Set<PluginServiceInfoJson> servicesJson = ImmutableSet.copyOf(Iterables.transform(services, new Function<PluginServiceInfo, PluginServiceInfoJson>() {

                            @Override
                            public PluginServiceInfoJson apply(final PluginServiceInfo input) {
                                return new PluginServiceInfoJson(input.getServiceTypeName(), input.getRegistrationName());
                            }
                        }));
                        return new PluginInfoJson(input.getBundleSymbolicName(),
                                                  input.getPluginName(),
                                                  input.getVersion(),
                                                  input.isRunning(),
                                                  servicesJson);
                    }
                }));

                return new NodeInfoJson(input.getKillbillVersion(),
                                        input.getApiVersion(),
                                        input.getPluginApiVersion(),
                                        input.getCommonVersion(),
                                        input.getPlatformVersion(),
                                        pluginsInfoJson);
            }
        }));

        return Response.status(Status.OK).entity(nodeInfosJson).build();
    }

}
