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

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.jaxrs.json.NodeCommandJson;
import org.killbill.billing.jaxrs.json.NodeCommandPropertyJson;
import org.killbill.billing.jaxrs.json.NodeInfoJson;
import org.killbill.billing.jaxrs.json.PluginInfoJson;
import org.killbill.billing.jaxrs.json.PluginInfoJson.PluginServiceInfoJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginServiceInfo;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.nodes.KillbillNodesApi;
import org.killbill.billing.util.nodes.NodeCommand;
import org.killbill.billing.util.nodes.NodeCommandMetadata;
import org.killbill.billing.util.nodes.NodeCommandProperty;
import org.killbill.billing.util.nodes.NodeInfo;
import org.killbill.billing.util.nodes.PluginNodeCommandMetadata;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.NODES_INFO_PATH)
@Api(value = JaxrsResource.NODES_INFO_PATH, description = "Operations to retrieve nodes info", tags="NodesInfo")
public class NodesInfoResource extends JaxRsResourceBase {

    private final KillbillNodesApi killbillInfoApi;

    @Inject
    public NodesInfoResource(final JaxrsUriBuilder uriBuilder,
                             final TagUserApi tagUserApi,
                             final CustomFieldUserApi customFieldUserApi,
                             final AuditUserApi auditUserApi,
                             final AccountUserApi accountUserApi,
                             final PaymentApi paymentApi,
                             final InvoicePaymentApi invoicePaymentApi,
                             final KillbillNodesApi killbillInfoApi,
                             final Clock clock,
                             final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
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
                                                  input.getPluginKey(),
                                                  input.getPluginName(),
                                                  input.getVersion(),
                                                  input.getPluginState().name(),
                                                  input.isSelectedForStart(),
                                                  servicesJson);
                    }
                }));

                return new NodeInfoJson(input.getNodeName(),
                                        input.getBootTime(),
                                        input.getLastUpdatedDate(),
                                        input.getKillbillVersion(),
                                        input.getApiVersion(),
                                        input.getPluginApiVersion(),
                                        input.getCommonVersion(),
                                        input.getPlatformVersion(),
                                        pluginsInfoJson);
            }
        }));

        return Response.status(Status.OK).entity(nodeInfosJson).build();
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Trigger a node command")
    @ApiResponses(value = {@ApiResponse(code = 202, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid node command supplied")})
    public Response triggerNodeCommand(final NodeCommandJson json,
                                       @QueryParam(QUERY_LOCAL_NODE_ONLY) @DefaultValue("false") final Boolean localNodeOnly,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException {

        final NodeCommandMetadata metadata = toNodeCommandMetadata(json);

        final NodeCommand nodeCommand = new NodeCommand() {
            @Override
            public boolean isSystemCommandType() {
                return json.isSystemCommandType();
            }
            @Override
            public String getNodeCommandType() {
                return json.getNodeCommandType();
            }
            @Override
            public NodeCommandMetadata getNodeCommandMetadata() {
                return metadata;
            }
        };

        killbillInfoApi.triggerNodeCommand(nodeCommand, localNodeOnly);
        return Response.status(Status.ACCEPTED).build();
    }


    private NodeCommandMetadata toNodeCommandMetadata(final NodeCommandJson input) {

        if (input.getNodeCommandProperties() == null || input.getNodeCommandProperties().isEmpty()) {
            return new NodeCommandMetadata() {
                @Override
                public List<NodeCommandProperty> getProperties() {
                    return ImmutableList.<NodeCommandProperty>of();
                }
            };
        }

        String pluginKey = null;
        String pluginName = null;
        String pluginVersion = null;
        final Iterator<NodeCommandPropertyJson> it = input.getNodeCommandProperties().iterator();
        while (it.hasNext()) {
            final NodeCommandProperty cur = it.next();
            if (PluginNodeCommandMetadata.PLUGIN_NAME.equals(cur.getKey())) {
                pluginName = (String) cur.getValue();
            } else if (PluginNodeCommandMetadata.PLUGIN_VERSION.equals(cur.getKey())) {
                pluginVersion = (String) cur.getValue();
            } else if (PluginNodeCommandMetadata.PLUGIN_KEY.equals(cur.getKey())) {
                pluginKey = (String) cur.getValue();
            }
            if (pluginName != null && pluginVersion != null && pluginKey != null) {
                break;
            }
        }

        if (pluginName != null || pluginKey != null) {
            removeFirstClassProperties(input.getNodeCommandProperties(), PluginNodeCommandMetadata.PLUGIN_NAME, PluginNodeCommandMetadata.PLUGIN_VERSION, PluginNodeCommandMetadata.PLUGIN_KEY);
            return new PluginNodeCommandMetadata(pluginKey, pluginName, pluginVersion, toNodeCommandProperties(input.getNodeCommandProperties()));
        } else {
            return new NodeCommandMetadata() {
                @Override
                public List<NodeCommandProperty> getProperties() {
                    return toNodeCommandProperties(input.getNodeCommandProperties());
                }
            };
        }
    }

    private void removeFirstClassProperties(final List<NodeCommandPropertyJson> properties, final String... toBeRemoved) {
        final Iterator<NodeCommandPropertyJson> it = properties.iterator();
        while (it.hasNext()) {
            final NodeCommandPropertyJson cur = it.next();
            for (String p : toBeRemoved) {
                if (cur.getKey().equals(p)) {
                    it.remove();
                    break;
                }
            }
        }
    }
    private List<NodeCommandProperty> toNodeCommandProperties(final List<NodeCommandPropertyJson> input) {
        return ImmutableList.copyOf(Iterables.transform(input, new Function<NodeCommandPropertyJson, NodeCommandProperty>() {
            @Override
            public NodeCommandProperty apply(final NodeCommandPropertyJson input) {
                return (NodeCommandProperty) input;
            }
        }));
    }
}
