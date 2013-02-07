/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
@Path(JaxrsResource.PLUGINS_PATH)
public class PluginResource extends JaxRsResourceBase {

    private final HttpServlet osgiServlet;

    @Inject
    public PluginResource(@Named("osgi") final HttpServlet osgiServlet,
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
        this.osgiServlet = osgiServlet;
    }

    @DELETE
    @Path("/{pluginName:" + STRING_PATTERN + "}/{rest:.+}")
    public Response doDELETE(@PathParam("pluginName") final String pluginName,
                             @javax.ws.rs.core.Context final HttpServletRequest request,
                             @javax.ws.rs.core.Context final HttpServletResponse response) throws ServletException, IOException {
        return serviceViaOSGIPlugin(pluginName, request, response);
    }

    @GET
    @Path("/{pluginName:" + STRING_PATTERN + "}/{rest:.+}")
    public Response doGET(@PathParam("pluginName") final String pluginName,
                          @javax.ws.rs.core.Context final HttpServletRequest request,
                          @javax.ws.rs.core.Context final HttpServletResponse response) throws ServletException, IOException {
        return serviceViaOSGIPlugin(pluginName, request, response);
    }

    @OPTIONS
    @Path("/{pluginName:" + STRING_PATTERN + "}/{rest:.+}")
    public Response doOPTIONS(@PathParam("pluginName") final String pluginName,
                              @javax.ws.rs.core.Context final HttpServletRequest request,
                              @javax.ws.rs.core.Context final HttpServletResponse response) throws ServletException, IOException {
        return serviceViaOSGIPlugin(pluginName, request, response);
    }

    @POST
    @Path("/{pluginName:" + STRING_PATTERN + "}/{rest:.+}")
    public Response doPOST(@PathParam("pluginName") final String pluginName,
                           @javax.ws.rs.core.Context final HttpServletRequest request,
                           @javax.ws.rs.core.Context final HttpServletResponse response) throws ServletException, IOException {
        return serviceViaOSGIPlugin(pluginName, request, response);
    }

    @PUT
    @Path("/{pluginName:" + STRING_PATTERN + "}/{rest:.+}")
    public Response doPUT(@PathParam("pluginName") final String pluginName,
                          @javax.ws.rs.core.Context final HttpServletRequest request,
                          @javax.ws.rs.core.Context final HttpServletResponse response) throws ServletException, IOException {
        return serviceViaOSGIPlugin(pluginName, request, response);
    }

    @HEAD
    @Path("/{pluginName:" + STRING_PATTERN + "}/{rest:.+}")
    public Response doHEAD(@PathParam("pluginName") final String pluginName,
                           @javax.ws.rs.core.Context final HttpServletRequest request,
                           @javax.ws.rs.core.Context final HttpServletResponse response) throws ServletException, IOException {
        prepareOSGIRequest(pluginName, request);
        osgiServlet.service(request, response);

        // Make sure to return 204
        return Response.noContent().build();
    }

    private Response serviceViaOSGIPlugin(final String pluginName, final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        prepareOSGIRequest(pluginName, request);
        osgiServlet.service(request, response);

        return Response.status(response.getStatus()).build();
    }

    private void prepareOSGIRequest(final String pluginName, final HttpServletRequest request) {
        request.setAttribute("killbill.osgi.pluginName", pluginName);
    }
}
