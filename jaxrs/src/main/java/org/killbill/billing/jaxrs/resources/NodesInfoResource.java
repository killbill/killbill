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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
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
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.nodes.KillbillNodesApi;
import org.killbill.billing.util.nodes.NodeCommand;
import org.killbill.billing.util.nodes.NodeCommandMetadata;
import org.killbill.billing.util.nodes.NodeCommandProperty;
import org.killbill.billing.util.nodes.NodeInfo;
import org.killbill.billing.util.nodes.PluginNodeCommandMetadata;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.api.annotation.TimedResource;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
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

    private PluginInfoJson mapPluginInfoToJson(final PluginInfo pluginInfo, final Set<PluginServiceInfoJson> servicesInfosJson) {
        return new PluginInfoJson(pluginInfo.getBundleSymbolicName(),
                                  pluginInfo.getPluginKey(),
                                  pluginInfo.getPluginName(),
                                  pluginInfo.getVersion(),
                                  pluginInfo.getPluginState().name(),
                                  pluginInfo.isSelectedForStart(),
                                  servicesInfosJson);
    }

    private NodeInfoJson mapNodeInfoToJson(final NodeInfo nodeInfo, final List<PluginInfoJson> pluginInfosJson) {
        return new NodeInfoJson(nodeInfo.getNodeName(),
                                nodeInfo.getBootTime(),
                                nodeInfo.getLastUpdatedDate(),
                                nodeInfo.getKillbillVersion(),
                                nodeInfo.getApiVersion(),
                                nodeInfo.getPluginApiVersion(),
                                nodeInfo.getCommonVersion(),
                                nodeInfo.getPlatformVersion(),
                                pluginInfosJson);
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve all the nodes infos", response = PluginInfoJson.class, responseContainer = "List")
    public Response getNodesInfo(@javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {

        final Iterable<NodeInfo> nodeInfos = killbillInfoApi.getNodesInfo();

        final List<NodeInfoJson> nodeInfosJson = Iterables.toStream(nodeInfos)
                .map(nodeInfo -> {
                    final List<PluginInfoJson> pluginInfosJson = Iterables.toStream(nodeInfo.getPluginInfo())
                            .map(pluginInfo -> {
                                final Set<PluginServiceInfoJson> servicesJson = pluginInfo.getServices().stream()
                                        .map(serviceInfo -> new PluginServiceInfoJson(serviceInfo.getServiceTypeName(), serviceInfo.getRegistrationName()))
                                        .collect(Collectors.toUnmodifiableSet());
                                return mapPluginInfoToJson(pluginInfo, servicesJson);
                            }).collect(Collectors.toUnmodifiableList());
                    return mapNodeInfoToJson(nodeInfo, pluginInfosJson);
                })
                .collect(Collectors.toUnmodifiableList());

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
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) {
        // Add in the broadcast message the Kill Bill version -- this is needed by the KPM plugin for instance
        final NodeInfo currentNodeInfo = killbillInfoApi.getCurrentNodeInfo();
        if (currentNodeInfo != null) {
            json.getNodeCommandProperties().add(new NodeCommandPropertyJson("kbVersion", currentNodeInfo.getKillbillVersion()));
        }

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
                    return Collections.emptyList();
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
        return input.stream()
                .map(node -> (NodeCommandProperty) node)
                .collect(Collectors.toUnmodifiableList());
    }
}
