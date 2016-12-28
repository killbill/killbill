/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.OverdueJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.overdue.api.OverdueApi;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;
import org.killbill.xmlloader.XMLLoader;
import org.killbill.xmlloader.XMLWriter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Singleton
@Path(JaxrsResource.OVERDUE_PATH)
@Api(value = JaxrsResource.OVERDUE_PATH, description = "Overdue information")
public class OverdueResource extends JaxRsResourceBase {

    private final OverdueApi overdueApi;

    @Inject
    public OverdueResource(final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final AccountUserApi accountUserApi,
                           final PaymentApi paymentApi,
                           final OverdueApi overdueApi,
                           final Clock clock,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
        this.overdueApi = overdueApi;
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_XML)
    @ApiOperation(value = "Retrieve the overdue config as XML", response = String.class, hidden = true)
    @ApiResponses(value = {})
    public Response getOverdueConfigXml(@javax.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        final TenantContext tenantContext = context.createContext(request);
        return Response.status(Status.OK).entity(XMLWriter.writeXML((DefaultOverdueConfig )overdueApi.getOverdueConfig(tenantContext), DefaultOverdueConfig.class)).build();
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_XML)
    @ApiOperation(value = "Upload the full overdue config as XML")
    @ApiResponses(value = {})
    public Response uploadOverdueConfigXml(final String overdueXML,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final HttpServletRequest request,
                                           @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        // Validation purpose:  Will throw if bad XML or catalog validation fails
        final InputStream stream = new ByteArrayInputStream(overdueXML.getBytes());
        XMLLoader.getObjectFromStream(new URI(JaxrsResource.OVERDUE_PATH), stream, DefaultOverdueConfig.class);

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        overdueApi.uploadOverdueConfig(overdueXML, callContext);
        return uriBuilder.buildResponse(uriInfo, OverdueResource.class, null, null, request);
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve the overdue config as JSON" , response = OverdueJson.class)
    @ApiResponses(value = {})
    public Response getOverdueConfigJson(@javax.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        final TenantContext tenantContext = context.createContext(request);
        final OverdueConfig overdueConfig = overdueApi.getOverdueConfig(tenantContext);
        final OverdueJson result = new OverdueJson(overdueConfig);
        return Response.status(Status.OK).entity(result).build();
    }



    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Upload the full overdue config as JSON")
    @ApiResponses(value = {})
    public Response uploadOverdueConfigJson(final OverdueJson overdueJson,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final OverdueConfig overdueConfig = OverdueJson.toOverdueConfigWithValidation(overdueJson);
        overdueApi.uploadOverdueConfig(overdueConfig, callContext);
        return uriBuilder.buildResponse(uriInfo, OverdueResource.class, null, null, request);
    }

}
